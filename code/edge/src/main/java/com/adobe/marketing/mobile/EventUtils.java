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

import java.util.HashMap;
import java.util.Map;

/**
 * Class for event or event data specific helpers.
 */
final class EventUtils {

	/**
	 * Checks if the provided {@code event} is of type {@link EdgeConstants.EventType#EDGE} and source {@link EdgeConstants.EventSource#REQUEST_CONTENT}.
	 *
	 * @param event the event to verify
	 * @return true if both type and source match, false otherwise
	 */
	static boolean isExperienceEvent(final Event event) {
		return (
			event != null &&
			EdgeConstants.EventType.EDGE.equalsIgnoreCase(event.getType()) &&
			EdgeConstants.EventSource.REQUEST_CONTENT.equalsIgnoreCase(event.getSource())
		);
	}

	/**
	 * Checks if the provided {@code event} is of type {@link EdgeConstants.EventType#EDGE} and source {@link EdgeConstants.EventSource#UPDATE_CONSENT}.
	 *
	 * @param event the event to verify
	 * @return true if both type and source match, false otherwise
	 */
	static boolean isUpdateConsentEvent(final Event event) {
		return (
			event != null &&
			EdgeConstants.EventType.EDGE.equalsIgnoreCase(event.getType()) &&
			EdgeConstants.EventSource.UPDATE_CONSENT.equalsIgnoreCase(event.getSource())
		);
	}

	/**
	 * Checks if the provided {@code event} is of type {@link EdgeConstants.EventType#CONSENT} and source {@link EdgeConstants.EventSource#RESPONSE_CONTENT}.
	 *
	 * @param event current event to check
	 * @return true if the type and source matches, false otherwise
	 */
	static boolean isConsentPreferencesUpdatedEvent(final Event event) {
		return (
			event != null &&
			EdgeConstants.EventType.CONSENT.equalsIgnoreCase(event.getType()) &&
			EdgeConstants.EventSource.RESPONSE_CONTENT.equalsIgnoreCase(event.getSource())
		);
	}

	/**
	 * Checks if the provided {@code event} is of type {@link EdgeConstants.EventType#EDGE_IDENTITY} and source {@link EdgeConstants.EventSource#RESET_COMPLETE}.
	 *
	 * @param event current event to check
	 * @return true if the type and source matches, false otherwise
	 */
	static boolean isResetComplete(final Event event) {
		return (
			event != null &&
			EdgeConstants.EventType.EDGE_IDENTITY.equalsIgnoreCase(event.getType()) &&
			EdgeConstants.EventSource.RESET_COMPLETE.equalsIgnoreCase(event.getSource())
		);
	}

	/**
	 * Checks if the provided {@code event} is of type {@link EdgeConstants.EventType#EDGE} and source
	 * {@link EdgeConstants.EventSource#REQUEST_IDENTITY} and the event data contains key of
	 * {@link EdgeConstants.EventDataKey#LOCATION_HINT} with value {@code true}.
	 *
	 * @param event current event to check
	 * @return true if the type, source, and key matches, false otherwise
	 */
	static boolean isGetLocationHintEvent(final Event event) {
		if (
			event != null &&
			EdgeConstants.EventType.EDGE.equalsIgnoreCase(event.getType()) &&
			EdgeConstants.EventSource.REQUEST_IDENTITY.equalsIgnoreCase(event.getSource()) &&
			event.getEventData() != null &&
			event.getEventData().containsKey(EdgeConstants.EventDataKey.LOCATION_HINT)
		) {
			try {
				Boolean isHintEvent = (Boolean) event.getEventData().get(EdgeConstants.EventDataKey.LOCATION_HINT);
				return isHintEvent != null && isHintEvent;
			} catch (ClassCastException e) {
				return false;
			}
		}

		return false;
	}

	/**
	 * Checks if the provided {@code event} is of type {@link EdgeConstants.EventType#EDGE} and source
	 * {@link EdgeConstants.EventSource#UPDATE_IDENTITY} and the event data contains key of
	 * {@link EdgeConstants.EventDataKey#LOCATION_HINT}.
	 *
	 * @param event current event to check
	 * @return true if the type and source matches, false otherwise
	 */
	static boolean isUpdateLocationHintEvent(final Event event) {
		return (
			event != null &&
			EdgeConstants.EventType.EDGE.equalsIgnoreCase(event.getType()) &&
			EdgeConstants.EventSource.UPDATE_IDENTITY.equalsIgnoreCase(event.getSource()) &&
			event.getEventData() != null &&
			event.getEventData().containsKey(EdgeConstants.EventDataKey.LOCATION_HINT)
		);
	}

	/**
	 * Checks if the provided {@code event} is null or if the event data is null/empty.
	 *
	 * @param event current event to check
	 * @return true if event is null, event data is null or empty map
	 */
	static boolean isNullEventOrEmptyData(final Event event) {
		return event == null || event.getEventData() == null || event.getEventData().isEmpty();
	}

	/**
	 * Extracts the Edge config values from the Configuration shared state payload,
	 * including {@code edge.configId}, {@code edge.environment}, and {@code edge.domain}.
	 *
	 * @param configSharedState shared state payload for Configuration
	 * @return all Edge config keys extracted from the {@code configSharedState}
	 */
	static Map<String, Object> getEdgeConfiguration(final Map<String, Object> configSharedState) {
		final String[] configKeysWithStringValue = new String[] {
			EdgeConstants.SharedState.Configuration.EDGE_CONFIG_ID,
			EdgeConstants.SharedState.Configuration.EDGE_REQUEST_ENVIRONMENT,
			EdgeConstants.SharedState.Configuration.EDGE_DOMAIN,
		};

		Map<String, Object> edgeConfig = new HashMap<>();

		for (String configKey : configKeysWithStringValue) {
			try {
				final String configValue = (String) configSharedState.get(configKey);

				if (!Utils.isNullOrEmpty(configValue)) {
					edgeConfig.put(configKey, configValue);
				}
			} catch (ClassCastException e) {
				MobileCore.log(
					LoggingMode.DEBUG,
					EdgeConstants.LOG_TAG,
					"EventUtils - Unable to read '" + configKey + "' due to incorrect format, expected string"
				);
			}
		}

		return edgeConfig;
	}

	/**
	 * Extracts {@code integrationid} from the Assurance shared state payload.
	 *
	 * @param assuranceSharedState shared state payload for Assurance
	 * @return value of {@code integrationid} or null if not found or conversion to String failed.
	 */
	static String getAssuranceIntegrationId(final Map<String, Object> assuranceSharedState) {
		String assuranceIntegrationId = null;

		if (!Utils.isNullOrEmpty(assuranceSharedState)) {
			try {
				assuranceIntegrationId =
					(String) assuranceSharedState.get(EdgeConstants.SharedState.Assurance.INTEGRATION_ID);
			} catch (ClassCastException e) {
				MobileCore.log(
					LoggingMode.VERBOSE,
					EdgeConstants.LOG_TAG,
					"EventUtils - Unable to extract Assurance integration id due to incorrect format, expected String"
				);
			}
		}

		return assuranceIntegrationId;
	}

	/**
	 * Checks if the provided {@code event} is a shared state update event for {@code stateOwnerName}
	 *
	 * @param stateOwnerName the shared state owner name; should not be null
	 * @param event current event to check; should not be null
	 * @return {@code boolean} indicating if it is the shared stage update for the provided {@code stateOwnerName}
	 */
	static boolean isSharedStateUpdateFor(final String stateOwnerName, final Event event) {
		if (Utils.isNullOrEmpty(stateOwnerName) || event == null) {
			return false;
		}

		String stateOwner;

		try {
			stateOwner = (String) event.getEventData().get(EdgeConstants.SharedState.STATE_OWNER);
		} catch (ClassCastException e) {
			return false;
		}

		return stateOwnerName.equals(stateOwner);
	}
}