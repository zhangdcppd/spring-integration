/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.integration.xml.config;

import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.integration.MessageRejectedException;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.support.SmartLifecycleRoleController;
import org.springframework.integration.test.util.TestUtils;
import org.springframework.integration.xml.AggregatedXmlMessageValidationException;
import org.springframework.integration.xml.util.XmlTestUtil;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.MultiValueMap;

/**
 * @author Jonas Partner
 * @author Oleg Zhurakousky
 * @author Gunnar Hillert
 * @author Artem Bilan
 * @author Gary Russell
 */
@ContextConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext
public class XmlPayloadValidatingFilterParserTests {

	public static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();

	static {
		DOCUMENT_BUILDER_FACTORY.setNamespaceAware(true);
		try {
			DOCUMENT_BUILDER_FACTORY.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
			DOCUMENT_BUILDER_FACTORY.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		}
		catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
	}

	@Autowired
	private ApplicationContext ac;

	@Test
	public void testParse() throws Exception {
		EventDrivenConsumer consumer = (EventDrivenConsumer) ac.getBean("parseOnly");
		assertEquals(2, TestUtils.getPropertyValue(consumer, "handler.order"));
		assertEquals(123L, TestUtils.getPropertyValue(consumer, "handler.messagingTemplate.sendTimeout"));
		assertEquals(-1, TestUtils.getPropertyValue(consumer, "phase"));
		assertFalse(TestUtils.getPropertyValue(consumer, "autoStartup", Boolean.class));
		SmartLifecycleRoleController roleController = ac.getBean(SmartLifecycleRoleController.class);
		@SuppressWarnings("unchecked")
		List<SmartLifecycle> list = (List<SmartLifecycle>) TestUtils.getPropertyValue(roleController, "lifecycles",
				MultiValueMap.class).get("foo");
		assertThat(list, contains((SmartLifecycle) consumer));
	}

	@Test
	public void testValidMessage() throws Exception {
		Message<String> docMessage =
				new GenericMessage<>("<!DOCTYPE greeting SYSTEM \"greeting.dtd\"><greeting>hello</greeting>");
		PollableChannel validChannel = this.ac.getBean("validOutputChannel", PollableChannel.class);
		MessageChannel inputChannel = this.ac.getBean("inputChannelA", MessageChannel.class);
		inputChannel.send(docMessage);
		assertNotNull(validChannel.receive(100));
	}

	@Test
	public void testInvalidMessageWithDiscardChannel() throws Exception {
		Document doc = XmlTestUtil.getDocumentForString("<greeting><other/></greeting>");
		GenericMessage<Document> docMessage = new GenericMessage<Document>(doc);
		PollableChannel validChannel = ac.getBean("validOutputChannel", PollableChannel.class);
		PollableChannel invalidChannel = ac.getBean("invalidOutputChannel", PollableChannel.class);
		MessageChannel inputChannel = ac.getBean("inputChannelA", MessageChannel.class);
		inputChannel.send(docMessage);
		assertNotNull(invalidChannel.receive(100));
		assertNull(validChannel.receive(100));
	}

	@Test
	public void testInvalidMessageWithThrowException() throws Exception {
		Document doc = XmlTestUtil.getDocumentForString("<greeting ping=\"pong\"><other/></greeting>");
		GenericMessage<Document> docMessage = new GenericMessage<Document>(doc);
		MessageChannel inputChannel = ac.getBean("inputChannelB", MessageChannel.class);
		try {
			inputChannel.send(docMessage);
			fail("MessageRejectedException expected");
		}
		catch (Exception e) {
			assertThat(e, Matchers.instanceOf(MessageRejectedException.class));
			Throwable cause = e.getCause();
			assertThat(cause, Matchers.instanceOf(AggregatedXmlMessageValidationException.class));
			assertThat(cause.getMessage(),
					Matchers.containsString("Element 'greeting' is a simple type, so it must have no element information item [children]."));
			assertThat(cause.getMessage(),
					Matchers.containsString("Element 'greeting' is a simple type, so it cannot have attributes,"));
		}
	}

	@Test
	public void testValidMessageWithValidator() throws Exception {
		Document doc = XmlTestUtil.getDocumentForString("<greeting>hello</greeting>");
		GenericMessage<Document> docMessage = new GenericMessage<Document>(doc);
		PollableChannel validChannel = ac.getBean("validOutputChannel", PollableChannel.class);
		MessageChannel inputChannel = ac.getBean("inputChannelC", MessageChannel.class);
		inputChannel.send(docMessage);
		assertNotNull(validChannel.receive(100));
	}

	@Test
	public void testInvalidMessageWithValidatorAndDiscardChannel() throws Exception {
		Document doc = XmlTestUtil.getDocumentForString("<greeting><other/></greeting>");
		GenericMessage<Document> docMessage = new GenericMessage<Document>(doc);
		PollableChannel invalidChannel = ac.getBean("invalidOutputChannel", PollableChannel.class);
		MessageChannel inputChannel = ac.getBean("inputChannelC", MessageChannel.class);
		inputChannel.send(docMessage);
		assertNotNull(invalidChannel.receive(100));
	}

}
