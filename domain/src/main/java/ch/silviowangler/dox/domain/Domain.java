/*
 * Copyright 2012 Silvio Wangler (silvio.wangler@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.silviowangler.dox.domain;

import org.hibernate.annotations.Sort;
import org.hibernate.annotations.SortType;
import org.springframework.data.jpa.domain.AbstractPersistable;

import javax.persistence.*;
import java.util.List;

/**
 * @author Silvio Wangler
 * @since 0.1
 */
@Entity
@Table(name = "DOX_DOMAIN")
public class Domain extends AbstractPersistable<Long> {

    @Column(unique = true, length = 15, nullable = false)
    private String shortName;

    @ElementCollection(fetch = FetchType.EAGER)
    @Sort(type = SortType.NATURAL)
    private List<String> values;

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }
}
