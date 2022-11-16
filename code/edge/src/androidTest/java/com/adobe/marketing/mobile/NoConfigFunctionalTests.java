/*
  Copyright 2021 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import static com.adobe.marketing.mobile.FunctionalTestHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.HttpMethod;
import com.adobe.marketing.mobile.services.TestableNetworkRequest;
import com.adobe.marketing.mobile.util.FakeIdentity;
import com.adobe.marketing.mobile.util.FunctionalTestConstants;
import com.adobe.marketing.mobile.util.FunctionalTestUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NoConfigFunctionalTests {

	private static final String EXEDGE_INTERACT_URL_STRING =
		FunctionalTestConstants.Defaults.EXEDGE_INTERACT_URL_STRING;

	@Rule
	public RuleChain rule = RuleChain
		.outerRule(new LogOnErrorRule())
		.around(new SetupCoreRule())
		.around(new RegisterMonitorExtensionRule());

	@Before
	public void setup() throws Exception {
		setExpectationEvent(EventType.HUB.getName(), EventSource.BOOTED.getName(), 1);
		setExpectationEvent(EventType.HUB.getName(), EventSource.SHARED_STATE.getName(), 2);

		Edge.registerExtension();
		FakeIdentity.registerExtension();

		final CountDownLatch latch = new CountDownLatch(1);
		MobileCore.start(
			new AdobeCallback() {
				@Override
				public void call(Object o) {
					latch.countDown();
				}
			}
		);

		latch.await();

		assertExpectedEvents(false);
		resetTestExpectations();
	}

	@Test
	public void testHandleExperienceEventRequest_withPendingConfigurationState_expectEventsQueueIsBlocked()
		throws Exception {
		Map<String, Object> configState = getSharedStateFor(
			FunctionalTestConstants.SharedState.CONFIGURATION,
			FunctionalTestConstants.Defaults.WAIT_SHARED_STATE_TIMEOUT_MS
		);
		assertNull(configState); // verify Configuration state is pending

		// Set "fake" ECID
		final String jsonStr =
			"{\n" +
			"      \"identityMap\": {\n" +
			"        \"ECID\": [\n" +
			"          {\n" +
			"            \"id\":" +
			"1234" +
			",\n" +
			"            \"authenticatedState\": \"ambiguous\",\n" +
			"            \"primary\": false\n" +
			"          }\n" +
			"        ]\n" +
			"      }\n" +
			"}";

		final JSONObject jsonObject = new JSONObject(jsonStr);
		final Map<String, Object> identityMap = Utils.toMap(jsonObject);

		resetTestExpectations(); // reset received events
		setExpectationEvent(
			FunctionalTestConstants.EventType.EDGE,
			FunctionalTestConstants.EventSource.REQUEST_CONTENT,
			1
		);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("testString", "xdm");
					}
				}
			)
			.build();
		Edge.sendEvent(experienceEvent, null);

		// verify the request event was only event sent
		assertExpectedEvents(false);

		// verify the network request was not sent
		List<TestableNetworkRequest> requests = getNetworkRequestsWith(EXEDGE_INTERACT_URL_STRING, HttpMethod.POST);
		assertTrue(requests.isEmpty());
	}

	@Test
	public void testCompletionHandler_withPendingConfigurationState_thenValidConfig_returnsEventHandles()
		throws Exception {
		final String responseBody =
			"\u0000{\"requestId\": \"0ee43289-4a4e-469a-bf5c-1d8186919a26\",\"handle\": [{\"payload\": [{\"id\": \"AT:eyJhY3Rpdml0eUlkIjoiMTE3NTg4IiwiZXhwZXJpZW5jZUlkIjoiMSJ9\",\"scope\": \"buttonColor\",\"items\": [{                           \"schema\": \"https://ns.adobe.com/personalization/json-content-item\",\"data\": {\"content\": {\"value\": \"#D41DBA\"}}}]}],\"type\": \"personalization:decisions\"},{\"payload\": [{\"type\": \"url\",\"id\": 411,\"spec\": {\"url\": \"//example.url?d_uuid=9876\",\"hideReferrer\": false,\"ttlMinutes\": 10080}}],\"type\": \"identity:exchange\"}]}\n";

		HttpConnecting responseConnection = createNetworkResponse(responseBody, 200);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, HttpMethod.POST, responseConnection);

		// Set "fake" ECID
		final String jsonStr =
			"{\n" +
			"      \"identityMap\": {\n" +
			"        \"ECID\": [\n" +
			"          {\n" +
			"            \"id\":" +
			"1234" +
			",\n" +
			"            \"authenticatedState\": \"ambiguous\",\n" +
			"            \"primary\": false\n" +
			"          }\n" +
			"        ]\n" +
			"      }\n" +
			"}";

		final JSONObject jsonObject = new JSONObject(jsonStr);
		final Map<String, Object> identityMap = Utils.toMap(jsonObject);
		FakeIdentity.setXDMSharedState(identityMap, FakeIdentity.EVENT_TYPE);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("eventType", "personalizationEvent");
						put("test", "xdm");
					}
				}
			)
			.build();

		final CountDownLatch latch = new CountDownLatch(1);
		final List<EdgeEventHandle> receivedHandles = new ArrayList<>();
		Edge.sendEvent(
			experienceEvent,
			new EdgeCallback() {
				@Override
				public void onComplete(List<EdgeEventHandle> handles) {
					receivedHandles.addAll(handles);
					latch.countDown();
				}
			}
		);

		List<TestableNetworkRequest> resultNetworkRequests = getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			HttpMethod.POST
		);
		assertTrue(resultNetworkRequests.isEmpty());

		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, HttpMethod.POST, 1);

		HashMap<String, Object> config = new HashMap<String, Object>() {
			{
				put("edge.configId", "123567");
			}
		};
		MobileCore.updateConfiguration(config);

		assertNetworkRequestCount();
		assertTrue(latch.await(1, TimeUnit.SECONDS));

		assertEquals(2, receivedHandles.size());

		// verify handle 1
		assertEquals("personalization:decisions", receivedHandles.get(0).getType());
		assertEquals(1, receivedHandles.get(0).getPayload().size());

		Map<String, String> handle1 = FunctionalTestUtils.flattenMap(receivedHandles.get(0).getPayload().get(0));

		assertEquals(4, handle1.size());
		assertEquals("AT:eyJhY3Rpdml0eUlkIjoiMTE3NTg4IiwiZXhwZXJpZW5jZUlkIjoiMSJ9", handle1.get("id"));
		assertEquals("#D41DBA", handle1.get("items[0].data.content.value"));
		assertEquals("https://ns.adobe.com/personalization/json-content-item", handle1.get("items[0].schema"));
		assertEquals("buttonColor", handle1.get("scope"));

		// verify handle 2
		assertEquals("identity:exchange", receivedHandles.get(1).getType());
		assertEquals(1, receivedHandles.get(1).getPayload().size());

		Map<String, String> handle2 = FunctionalTestUtils.flattenMap(receivedHandles.get(1).getPayload().get(0));

		assertEquals(5, handle2.size());
		assertEquals("411", handle2.get("id"));
		assertEquals("url", handle2.get("type"));
		assertEquals("//example.url?d_uuid=9876", handle2.get("spec.url"));
		assertEquals("false", handle2.get("spec.hideReferrer"));
		assertEquals("10080", handle2.get("spec.ttlMinutes"));
	}
}