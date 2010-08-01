/*
 * Copyright 2002-2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.integration.config.xml;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.xml.AbstractSimpleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

/**
 * @author Oleg Zhurakousky
 * @since 2.0
 */
public class MessageHistoryParser extends AbstractSimpleBeanDefinitionParser {
	private String messageHistory;

	@Override
	protected String getBeanClassName(Element element) {
		return "org.springframework.integration.history.MessageHistoryWriter";
	}

	@Override
	protected boolean shouldGenerateId() {
		return false;
	}
	
	protected String resolveId(Element element, AbstractBeanDefinition definition, ParserContext parserContext){
		if (messageHistory == null){
			messageHistory = BeanDefinitionReaderUtils.generateBeanName(definition, parserContext.getRegistry());
		} else {
			throw new BeanDefinitionStoreException("Attempt to register more then one MessageHistoryWriter");
		}
		return messageHistory;
	}
}