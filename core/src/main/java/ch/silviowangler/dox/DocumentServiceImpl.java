package ch.silviowangler.dox;

import ch.silviowangler.dox.api.*;
import ch.silviowangler.dox.domain.*;
import ch.silviowangler.dox.domain.DocumentClass;
import com.itextpdf.text.pdf.PdfReader;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * @author Silvio Wangler
 * @version 0.1
 */
@Service
public class DocumentServiceImpl implements DocumentService, InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private DocumentClassRepository documentClassRepository;
    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private AttributeRepository attributeRepository;
    @Autowired
    private IndexStoreRepository indexStoreRepository;
    @Value("#{systemEnvironment['DOX_STORE']}")
    private File archiveDirectory;
    @Autowired
    private Properties mimeTypes;

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(archiveDirectory, "Archive directory must not be null");
        Assert.isTrue(archiveDirectory.isDirectory(), "Archive store must be a directory ['" + this.archiveDirectory + "']");
        Assert.isTrue(archiveDirectory.canRead(), "Archive store must be readable ['" + this.archiveDirectory + "']");
        Assert.isTrue(archiveDirectory.canWrite(), "Archive store must be writable ['" + this.archiveDirectory + "']");
        Assert.notEmpty(mimeTypes, "No mime types have been set");
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public PhysicalDocument findPhysicalDocument(Long id) throws DocumentNotFoundException, DocumentNotInStoreException {

        DocumentReference doc = findDocumentReference(id);
        PhysicalDocument document = new PhysicalDocument();

        try {
            BeanUtils.copyProperties(document, doc);
        } catch (IllegalAccessException e) {
            logger.error("Unable to copy properties", e);
        } catch (InvocationTargetException e) {
            logger.error("Unable to copy properties", e);
        }

        File file = new File(this.archiveDirectory, doc.getHash());

        if (!file.exists()) {
            throw new DocumentNotInStoreException(doc.getHash(), doc.getId());
        }

        return document;
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public DocumentReference findDocumentReference(Long id) throws DocumentNotFoundException {
        logger.info("About to find document by using id {}", id);

        if (documentReferenceExists(id)) {
            logger.info("Found document for id {}", id);
            Document document = documentRepository.findOne(id);

            DocumentReference documentReference = toDocumentReference(document);

            return documentReference;
        } else {
            logger.warn("No document found for id {}", id);
            throw new DocumentNotFoundException(id);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public DocumentReference importDocument(PhysicalDocument physicalDocument) throws ValdiationException, DocumentDuplicationException {

        final String documentClassShortName = physicalDocument.getDocumentClass().getShortName();
        DocumentClass documentClassEntity = documentClassRepository.findByShortName(documentClassShortName);

        if (documentClassEntity == null) {
            logger.error("No such document class with name '{}' found", documentClassShortName);
            throw new ValdiationException("No such document class with name '" + documentClassShortName + "' available");
        }

        List<Attribute> attributes = attributeRepository.findAttributesForDocumentClass(documentClassEntity);

        logger.debug("Found {} attributes for document class '{}'", attributes.size(), documentClassShortName);

        verifyMandatoryAttributes(physicalDocument, attributes);
        verifyUnknownKeys(physicalDocument, documentClassShortName, attributes);

        physicalDocument.setIndexes(fixDataTypesOfIndices(physicalDocument.getIndexes(), attributes));

        final String mimeType = investigateMimeType(physicalDocument.getFileName());
        final String hash = DigestUtils.sha256Hex(physicalDocument.getContent());

        final Document documentByHash = documentRepository.findByHash(hash);
        if (documentByHash != null) {
            throw new DocumentDuplicationException(documentByHash.getId(), hash);
        }

        final int numberOfPages = getNumberOfPages(physicalDocument, mimeType);

        IndexStore indexStore = new IndexStore();
        updateIndices(physicalDocument, indexStore);
        indexStore = indexStoreRepository.save(indexStore);


        Document document = new Document(hash, documentClassEntity, numberOfPages, mimeType, physicalDocument.getFileName(), indexStore);
        document = documentRepository.save(document);

        File target = new File(this.archiveDirectory, hash);
        try {
            FileUtils.writeByteArrayToFile(target, physicalDocument.getContent());
        } catch (IOException e) {
            logger.error("Unable to write content to store", e);
        }
        DocumentReference docRef = toDocumentReference(document);
        return docRef;
    }

    private String investigateMimeType(final String fileName) {

        logger.debug("Trying to find media type (mime type) for file name '{}'", fileName);

        int i = fileName.lastIndexOf('.');
        String extension = null;

        if (i > 0 && i < fileName.length() - 1) {
            extension = fileName.substring(i + 1).toLowerCase();
        }

        if (this.mimeTypes.containsKey(extension)) {
            return (String) this.mimeTypes.get(extension);
        }
        logger.error("No media type (mime type) registered for file extension '{}' (original file name '{}')", extension, fileName);
        throw new UnsupportedOperationException("No mime type registered for file extension " + fileName);
    }

    private Map<String, Object> fixDataTypesOfIndices(final Map<String, Object> indexes, List<Attribute> attributes) {

        Map<String, Object> resultMap = new HashMap<String, Object>(indexes); // copy elements

        for (Attribute attribute : attributes) {

            final String attributeShortName = attribute.getShortName();
            if (resultMap.containsKey(attributeShortName)) {
                if (!isAssignableType(attribute.getDataType(), resultMap.get(attributeShortName).getClass())) {
                    logger.debug("Attribute '{}' is not assignable to '{}'", attributeShortName, attribute.getDataType());
                    resultMap.put(attributeShortName, makeAssignable(attribute.getDataType(), resultMap.get(attributeShortName)));
                }
            } else {
                logger.debug("Ignoring attribute '{}' since it was not mentioned in the index map", attributeShortName);
            }
        }
        return resultMap;
    }

    private Object makeAssignable(AttributeDataType desiredDataType, Object valueToConvert) {

        if (AttributeDataType.DATE == desiredDataType && valueToConvert instanceof String) {
            return DateTimeFormat.forPattern("dd.MM.yyyy").parseDateTime((String) valueToConvert);
        } else if (AttributeDataType.DATE == desiredDataType && valueToConvert instanceof Date) {
            return new DateTime(valueToConvert);
        }
        return new IllegalArgumentException("Unable to convert data type " + desiredDataType + " and value " + valueToConvert + "(Class: '" + valueToConvert.getClass().getCanonicalName() + "')");
    }

    private boolean isAssignableType(AttributeDataType desiredDataType, Class currentType) {

        if (desiredDataType == AttributeDataType.DATE) {
            return currentType.isAssignableFrom(DateTime.class);
        } else if (desiredDataType == AttributeDataType.STRING) {
            return currentType.isAssignableFrom(String.class);
        } else {
            logger.error("Unknown data type '{}'", desiredDataType);
            throw new IllegalArgumentException("Unknown data type " + desiredDataType);
        }
    }

    private void updateIndices(DocumentReference physicalDocument, IndexStore indexStore) {
        for (String key : physicalDocument.getIndexes().keySet()) {

            Attribute attribute = attributeRepository.findByShortName(key);
            assert attribute != null : "Attribute " + key + " must be there";

            final Object value = physicalDocument.getIndexes().get(key);
            try {
                final String propertyName = attribute.getMappingColumn().toLowerCase();
                logger.debug("About to set column '{}' using value '{}' on index store", propertyName, value);
                PropertyUtils.setProperty(indexStore, propertyName, value);
            } catch (IllegalAccessException e) {
                logger.error("Error setting property '{}' with value '{}'", new Object[]{key, value, e});
            } catch (InvocationTargetException e) {
                logger.error("Error setting property '{}' with value '{}'", new Object[]{key, value, e});
            } catch (NoSuchMethodException e) {
                logger.error("Error setting property '{}' with value '{}'", new Object[]{key, value, e});
            }
        }
    }

    private void verifyUnknownKeys(PhysicalDocument physicalDocument, String documentClassShortName, List<Attribute> attributes) throws ValdiationException {
        for (String key : physicalDocument.getIndexes().keySet()) {
            boolean exists = false;
            for (Attribute attribute : attributes) {
                if (attribute.getShortName().equals(key)) {
                    exists = true;
                    continue;
                }
            }
            if (!exists) {
                logger.warn("Key '{}' does not belong to document class '{}'", documentClassShortName);
                throw new ValdiationException("Key " + key + " does not belong to document class " + documentClassShortName);
            }
        }
    }

    private void verifyMandatoryAttributes(PhysicalDocument physicalDocument, List<Attribute> attributes) throws ValdiationException {
        for (Attribute attribute : attributes) {
            if (!attribute.isOptional()) {
                if (!physicalDocument.getIndexes().containsKey(attribute.getShortName())) {
                    logger.warn("Attribute '{}' is required for document class(es) '{}' and not was not provided.", attribute.getShortName(), attribute.getDocumentClasses());
                    throw new ValdiationException("Attribute " + attribute.getShortName() + " is mandatory");
                }
            }
        }
    }

    private int getNumberOfPages(final PhysicalDocument physicalDocument, final String mimeType) {

        int numberOfPages = -1;

        if ("application/pdf".equals(mimeType)) {

            PdfReader pdfReader;
            try {
                pdfReader = new PdfReader(physicalDocument.getContent());
            } catch (IOException e) {
                logger.error("Unable to determine the number of pages", e);
                return numberOfPages;
            }
            numberOfPages = pdfReader.getNumberOfPages();
        } else if ("text/plain".equals(mimeType)) {
            numberOfPages = -1;
        } else {
            throw new UnsupportedOperationException("Cannot determine page count from a document with a mime type '" + mimeType + "'");
        }
        logger.debug("Found {} page(s) on physical document {}", numberOfPages, physicalDocument);
        return numberOfPages;
    }

    private boolean documentReferenceExists(Long id) {
        return documentRepository.exists(id);
    }

    private DocumentReference toDocumentReference(Document document) {
        return new DocumentReference(document.getHash(),
                document.getId(),
                document.getPageCount(),
                document.getMimeType(),
                toDocumentClassApi(document.getDocumentClass()),
                toIndexMap(document.getIndexStore(), attributeRepository.findAttributesForDocumentClass(document.getDocumentClass())),
                document.getOriginalFilename());
    }

    private Map<String, Object> toIndexMap(IndexStore indexStore, List<Attribute> attributes) {

        Map<String, Object> indices = new HashMap<String, Object>(attributes.size());

        for (Attribute attribute : attributes) {
            try {
                indices.put(attribute.getShortName(), PropertyUtils.getProperty(indexStore, attribute.getMappingColumn().toLowerCase()));
            } catch (IllegalAccessException e) {
                logger.error("Error setting property '{}'", attribute.getShortName(), e);
            } catch (InvocationTargetException e) {
                logger.error("Error setting property '{}'", attribute.getShortName(), e);
            } catch (NoSuchMethodException e) {
                logger.error("Error setting property '{}'", attribute.getShortName(), e);
            }
        }
        return indices;
    }

    private ch.silviowangler.dox.api.DocumentClass toDocumentClassApi(DocumentClass documentClass) {
        return new ch.silviowangler.dox.api.DocumentClass(documentClass.getShortName());
    }
}
