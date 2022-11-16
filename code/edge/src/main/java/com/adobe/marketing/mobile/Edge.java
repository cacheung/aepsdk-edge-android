/*
  Copyright 2019 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import static com.adobe.marketing.mobile.EdgeConstants.LOG_TAG;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class Edge {

	private Edge() {}

	/**
	 * Returns the version of the {@code Edge} extension
	 *
	 * @return The version as {@code String}
	 */
	public static String extensionVersion() {
		return EdgeConstants.EXTENSION_VERSION;
	}

	/**
	 * Registers the extension with the Mobile SDK. This method should be called only once in your application class.
	 */
	public static void registerExtension() {
		MobileCore.registerExtension(
			EdgeExtension.class,
			new ExtensionErrorCallback<ExtensionError>() {
				@Override
				public void error(ExtensionError extensionError) {
					MobileCore.log(
						LoggingMode.ERROR,
						LOG_TAG,
						"Edge - There was an error registering the Edge extension: " + extensionError.getErrorName()
					);
				}
			}
		);
	}

	/**
	 * Sends an event to Adobe Experience Edge and registers a callback for responses coming from the Edge Network.
	 *
	 * @param experienceEvent event to be sent to Adobe Experience Edge; should not be null
	 * @param callback        optional callback to be invoked when the request is complete, returning the associated response handles
	 *                        received from the Adobe Experience Edge. It may be invoked on a different thread.
	 */
	public static void sendEvent(final ExperienceEvent experienceEvent, final EdgeCallback callback) {
		if (experienceEvent == null) {
			MobileCore.log(
				LoggingMode.WARNING,
				LOG_TAG,
				"Edge - sendEvent API cannot make request, the ExperienceEvent should not be null."
			);
			return;
		}

		// Note: iOS implementation ignores requests if XDM data is empty
		if (Utils.isNullOrEmpty(experienceEvent.getXdmSchema())) {
			MobileCore.log(
				LoggingMode.WARNING,
				LOG_TAG,
				"Edge - sendEvent API cannot make request with null/empty XDM data."
			);
			return;
		}

		// serialize the ExperienceEvent into event data
		final Map<String, Object> data = experienceEvent.toObjectMap();

		if (Utils.isNullOrEmpty(data)) {
			MobileCore.log(
				LoggingMode.WARNING,
				LOG_TAG,
				"Edge - sendEvent API cannot make request with null/empty event data."
			);
			return;
		}

		Event event = new Event.Builder(
			EdgeConstants.EventName.REQUEST_CONTENT,
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.REQUEST_CONTENT
		)
			.setEventData(data)
			.build();

		ExtensionErrorCallback<ExtensionError> errorCallback = new ExtensionErrorCallback<ExtensionError>() {
			@Override
			public void error(final ExtensionError extensionError) {
				MobileCore.log(
					LoggingMode.WARNING,
					LOG_TAG,
					"Edge - There was an error while sending the request to Experience Edge: " +
					extensionError.getErrorName()
				);
			}
		};

		// dispatch created event to the event hub & register response callback
		CompletionCallbacksManager.getInstance().registerCallback(event.getUniqueIdentifier(), callback);
		MobileCore.dispatchEvent(event, errorCallback);
	}

	/**
	 * Gets the Edge Network location hint used in requests to the Adobe Experience Platform Edge Network.
	 * The Edge Network location hint may be used when building the URL for Adobe Experience Platform Edge Network
	 * requests to hint at the server cluster to use.
	 * Returns the Edge Network location hint, or null if the location hint expired or is not set.
	 *
	 * @param callback {@link AdobeCallback} of {@code String} invoked with a value containing the Edge Network location hint.
	 *                 The returned location hint may be null if the hint expired or none is set.
	 *     	           If an {@link AdobeCallbackWithError} is provided, an {@link AdobeError} can be returned in the
	 *	               eventuality of an error.
	 */
	public static void getLocationHint(final AdobeCallback<String> callback) {
		if (callback == null) {
			MobileCore.log(
				LoggingMode.DEBUG,
				LOG_TAG,
				"Edge - Unexpected null callback, provide a callback to receive current location hint."
			);
			return;
		}

		final Map<String, Object> requestData = new HashMap<String, Object>() {
			{
				put(EdgeConstants.EventDataKey.LOCATION_HINT, true);
			}
		};

		final Event event = new Event.Builder(
			EdgeConstants.EventName.REQUEST_LOCATION_HINT,
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.REQUEST_IDENTITY
		)
			.setEventData(requestData)
			.build();

		ExtensionErrorCallback<ExtensionError> errorCallback = new ExtensionErrorCallback<ExtensionError>() {
			@Override
			public void error(final ExtensionError extensionError) {
				MobileCore.log(
					LoggingMode.WARNING,
					LOG_TAG,
					String.format(
						"Edge - There was an error while dispatching event '%s': %s",
						EdgeConstants.EventName.REQUEST_LOCATION_HINT,
						extensionError.getErrorName()
					)
				);
			}
		};

		MobileCore.dispatchEventWithResponseCallback(
			event,
			new AdobeCallback<Event>() {
				@Override
				public void call(Event responseEvent) {
					if (responseEvent == null) {
						returnError(callback, AdobeError.CALLBACK_TIMEOUT);
						return;
					}

					final Map<String, Object> responseData = responseEvent.getEventData();

					if (responseData == null || !responseData.containsKey(EdgeConstants.EventDataKey.LOCATION_HINT)) {
						returnError(callback, AdobeError.UNEXPECTED_ERROR);
						return;
					}

					try {
						String locationHint = (String) responseData.get(EdgeConstants.EventDataKey.LOCATION_HINT);
						callback.call(locationHint); // hint may be null (hint not set or expired)
					} catch (ClassCastException e) {
						returnError(callback, AdobeError.UNEXPECTED_ERROR); // hint value wrong type
						MobileCore.log(
							LoggingMode.WARNING,
							LOG_TAG,
							"Edge - Failed to parse getLocationHint value to String. " + e.getLocalizedMessage()
						);
						return;
					}
				}
			},
			errorCallback
		);
	}

	/**
	 * Sets the Edge Network location hint used in requests to the Adobe Experience Platform Edge Network.
	 * Sets the Edge Network location hint used in requests to the AEP Edge Network causing requests
	 * to "stick" to a specific server cluster. Passing null or an empty string clears the existing
	 * location hint. Edge Network responses may overwrite the location hint to a new value when necessary
	 * to manage network traffic.
	 * <p>
	 * Use caution when setting the location hint. Only use location hints for the "EdgeNetwork" scope.
	 * An incorrect location hint value will cause all Edge Network requests to fail.
	 *
	 * @param hint the Edge Network location hint to use when connecting to the Adobe Experience Platform Edge Network
	 */
	public static void setLocationHint(final String hint) {
		Map<String, Object> requestData = new HashMap<String, Object>() {
			{
				put(EdgeConstants.EventDataKey.LOCATION_HINT, hint);
			}
		};

		final Event event = new Event.Builder(
			EdgeConstants.EventName.UPDATE_LOCATION_HINT,
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.UPDATE_IDENTITY
		)
			.setEventData(requestData)
			.build();

		ExtensionErrorCallback<ExtensionError> errorCallback = new ExtensionErrorCallback<ExtensionError>() {
			@Override
			public void error(final ExtensionError extensionError) {
				MobileCore.log(
					LoggingMode.WARNING,
					LOG_TAG,
					String.format(
						"Edge - There was an error while dispatching event '%s': %s",
						EdgeConstants.EventName.UPDATE_LOCATION_HINT,
						extensionError.getErrorName()
					)
				);
			}
		};
		MobileCore.dispatchEvent(event, errorCallback);
	}

	/**
	 * When an {@link AdobeCallbackWithError} is provided, the fail method will be called with provided {@link AdobeError}.
	 *
	 * @param <T> the type passed to the {@code AdobeCallback} method
	 * @param callback should not be null, should be instance of {@code AdobeCallbackWithError}
	 * @param error    the {@code AdobeError} returned back in the callback
	 */
	private static <T> void returnError(final AdobeCallback<T> callback, final AdobeError error) {
		if (callback == null) {
			return;
		}

		final AdobeCallbackWithError<T> adobeCallbackWithError = callback instanceof AdobeCallbackWithError
			? (AdobeCallbackWithError<T>) callback
			: null;

		if (adobeCallbackWithError != null) {
			adobeCallbackWithError.fail(error);
		}
	}
}