package ch.silviowangler.dox.web;

import ch.silviowangler.dox.api.Attribute;
import ch.silviowangler.dox.api.AttributeDataType;
import ch.silviowangler.dox.api.DocumentClass;
import ch.silviowangler.dox.api.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.mobile.device.Device;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.HtmlUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author Silvio Wangler
 * @since 0.1
 *        <div>
 *        Date: 17.07.12 08:22
 *        </div>
 */
@Controller
public class ImportController implements MessageSourceAware, InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private DocumentService documentService;
    private MessageSource messageSource;

    @Override
    public void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(this.messageSource, "Message source must not be null");
    }

    @RequestMapping(method = RequestMethod.GET, value = "import.html")
    public ModelAndView query() {
        Map<String, Object> model = new HashMap<String, Object>();
        model.put("documentClasses", documentService.findDocumentClasses());
        model.put("docClass", "");
        return new ModelAndView("import.definition", model);
    }

    @RequestMapping(method = RequestMethod.POST, value = "/ajax/attributes")
    @ResponseBody
    public String getAttributeForm(@RequestParam("documentClassShortName") String documentClassShortName, Locale locale, Device device) {

        logger.debug("Normal device: {}, mobile device: {}, tablet device: {}", new Boolean[]{device.isNormal(), device.isMobile(), device.isTablet()});
        logger.debug("About to generate form for document class '{}'", documentClassShortName);

        Set<Attribute> attributes = documentService.findAttributes(new DocumentClass(documentClassShortName));
        StringBuffer sb;
        String html;

        if (!attributes.isEmpty()) {
            sb = new StringBuffer("<form id=\"fileUpload\" method=\"POST\" action=\"performImport.html\" enctype=\"multipart/form-data\">\n");
            sb.append("<input name=\"documentClassShortName\" type=\"hidden\" value=\"").append(documentClassShortName).append("\"/>\n");

            for (Attribute attribute : attributes) {
                sb.append("<label for=\"").append(attribute.getShortName()).append("\">");
                sb.append(messageSource.getMessage("attr." + attribute.getShortName(), null, attribute.getShortName(), locale))
                        .append(":");

                if (!attribute.isOptional()) {
                    sb.append(" <span class=\"required\">*</span>");
                }
                sb.append("</label>\n");

                if (attribute.hasDomainValues()) {

                    sb.append("<datalist id=\"list-").append(attribute.getShortName()).append("\">");

                    for (String value : attribute.getDomainValues()) {
                        sb.append("<option value=\"").append(value).append("\"/>");
                    }
                    sb.append("</datalist>\n");
                    sb.append("<input name=\"").append(attribute.getShortName()).append("\" list=\"list-")
                            .append(attribute.getShortName()).append("\"/>\n");
                } else {
                    sb.append("<input name=\"").append(attribute.getShortName()).append("\" type=\"")
                            .append(getInputType(attribute.getDataType())).append("\"/>\n");
                }
            }

            sb.append("<input name=\"file\" type=\"file\"/>\n");

            sb.append("<button type=\"submit\" id=\"importDocBtn\">")
                    .append(messageSource.getMessage("document.import.button.submit", null, locale))
                    .append("</button>\n");
            sb.append("</form>");

        } else {
            sb = new StringBuffer("<ul id=\"errors\">");

            final String message = HtmlUtils.htmlEscape(messageSource.getMessage("document.import.no.attributes", new Object[]{documentClassShortName}, locale));
            sb.append("<li id=\"info\">").append(message).append("</li>");
            sb.append("</ul>");
        }
        html = sb.toString();
        logger.debug("Returning HTML code {}", html);
        return html;
    }

    private String getInputType(AttributeDataType dataType) {

        if (AttributeDataType.DATE.equals(dataType)) return "date";
        else if (AttributeDataType.LONG.equals(dataType)) return "number";
        else if (AttributeDataType.DOUBLE.equals(dataType)) return "number";
        else if (AttributeDataType.SHORT.equals(dataType)) return "number";
        else if (AttributeDataType.INTEGER.equals(dataType)) return "number";

        return "text";
    }
}