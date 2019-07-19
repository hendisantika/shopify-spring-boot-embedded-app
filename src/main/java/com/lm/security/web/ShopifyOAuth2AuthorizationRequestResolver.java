package com.lm.security.web;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.util.UriComponentsBuilder;

import com.lm.security.service.TokenService;

/*
 * This class is called by OAuth2AuthorizationRequestRedirectFilter to save the OAuth2AuthorizationRequest
 * when this app is being installed (embedded app) or if the user want to log in (not embedded).
 * 
 * The resolve(req) method returns null to prevent redirection (as this is taken care of by the Shopify javascript).
 * 
 * However, if no shop is provided as a parameter, an OAuth2AuthorizationRequest is returned to redirect to log in.
 * It is implicit, so that it will not be saved by the filter.
 * 
 */
public class ShopifyOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
	public static final String SHOPIFY_SHOP_PARAMETER_KEY_FOR_TOKEN = "shop"; // must match template variable in ClientRegistration token_uri
	
	private ClientRegistrationRepository clientRegistrationRepository;
	private AntPathRequestMatcher authorizationRequestMatcher;
	private final StringKeyGenerator stateGenerator = new Base64StringKeyGenerator(Base64.getUrlEncoder());
	private final ShopifyRedirectStrategy authorizationRedirectStrategy = new ShopifyRedirectStrategy();
	private final ShopifyHttpSessionOAuth2AuthorizationRequestRepository customAuthorizationRequestRepository;
	private final String loginUri;

	
	public ShopifyOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository, 
							ShopifyHttpSessionOAuth2AuthorizationRequestRepository customAuthorizationRequestRepository,
							String authorizationRequestBaseUri, String loginUri) {

		this.clientRegistrationRepository = clientRegistrationRepository;
		this.customAuthorizationRequestRepository = customAuthorizationRequestRepository;
		this.authorizationRequestMatcher = new AntPathRequestMatcher(
				authorizationRequestBaseUri + "/{registrationId}");
		this.loginUri = loginUri;
	}
	

	/*
	 * In DefaultOAuth2AuthorizationRequestResolver, this method is expected to redirect the user to log in
	 * 
	 */
	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {

		// is already properly authenticated, skip
		if(isAuthenticated(request)) {
			return null;
		}

		// extract the registrationId (ex: "shopify")
		String registrationId;
		
		if (this.authorizationRequestMatcher.matches(request)) {
			registrationId = this.authorizationRequestMatcher
					.extractUriTemplateVariables(request).get("registrationId");

			if(registrationId == null || registrationId.isEmpty()) {

				throw new IllegalArgumentException("Invalid registration id");
			}
		} else {
			return null;

		}

		// At this point, either the request came from Shopify, or make sure shop param was provided
		String shopName = null;
				
		shopName = this.getShopName(request);
				
		if(shopName == null || shopName.isEmpty() || registrationId == null) {
			// shop name is required, or registrationId
			// trigger a redirect
			return redirectToLogin();
		}
		
		// obtain a ClientRegistration for extracted registrationId
		ClientRegistration clientRegistration = this.clientRegistrationRepository.findByRegistrationId(registrationId);
		if (clientRegistration == null) {
			throw new IllegalArgumentException("Invalid Client Registration: " + registrationId);
		}

		// only the Authorization code grant is accepted
		OAuth2AuthorizationRequest.Builder builder;
		if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(clientRegistration.getAuthorizationGrantType())) {
			builder = OAuth2AuthorizationRequest.authorizationCode();
		} else {
			throw new IllegalArgumentException("Invalid Authorization Grant Type ("  +
					clientRegistration.getAuthorizationGrantType().getValue() +
					") for Client Registration: " + clientRegistration.getRegistrationId());
		}
		
		String redirectUriStr = this.expandRedirectUri(request, clientRegistration);

		Map<String, Object> additionalParameters = new HashMap<>();
		additionalParameters.put(OAuth2ParameterNames.REGISTRATION_ID, clientRegistration.getRegistrationId());
		additionalParameters.put(SHOPIFY_SHOP_PARAMETER_KEY_FOR_TOKEN, shopName);
		
		OAuth2AuthorizationRequest authorizationRequest = builder
				.clientId(clientRegistration.getClientId())
				.authorizationUri(this.generateAuthorizationUri(request, clientRegistration.getProviderDetails().getAuthorizationUri()))
				.redirectUri(redirectUriStr)
				.scopes(clientRegistration.getScopes())
				.state(this.stateGenerator.generateKey())
				.additionalParameters(additionalParameters)
				.build();


		// Save the OAuth2AuthorizationRequest
		customAuthorizationRequestRepository.saveAuthorizationRequest(authorizationRequest, request);

		// DO NOT redirect, build redirecturi: DefaultRedirectStrategy		
		authorizationRedirectStrategy.saveRedirectAuthenticationUris(request, authorizationRequest);
		
		return null;
	}

	
	
	/* Method called by the OAuth2RequestRedirectFilter to handle a ClientAuthorizationRequiredException
	* and create a redirect uri to the authorization server.
	* This scenario should never occur, so return null.
	*/
	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest req, String registrationId) {

		return null;
	}
	
	private String expandRedirectUri(HttpServletRequest request, ClientRegistration clientRegistration) {
		// Supported URI variables -> baseUrl, registrationId
		// EX: "{baseUrl}/oauth2/code/{registrationId}"
		Map<String, String> uriVariables = new HashMap<>();
		uriVariables.put("registrationId", clientRegistration.getRegistrationId());

		String baseUrl = UriComponentsBuilder.fromHttpUrl(UrlUtils.buildFullRequestUrl(request))
				.replaceQuery(null)
				.replacePath(request.getContextPath())
				.build()
				.toUriString();
		uriVariables.put("baseUrl", baseUrl);

		return UriComponentsBuilder.fromUriString(clientRegistration.getRedirectUriTemplate())
				.buildAndExpand(uriVariables)
				.toUriString();
	}
	
	
	/*
	 * Expects a shop request parameter to generate the authorization uri
	 */
	private String generateAuthorizationUri(HttpServletRequest request, String authorizationUriTemplate) {
		String shopName = this.getShopName(request);
		
		Map<String, String> uriVariables = new HashMap<>();
		uriVariables.put(SHOPIFY_SHOP_PARAMETER_KEY_FOR_TOKEN, shopName);
		
		String authorizationUri = UriComponentsBuilder
							.fromHttpUrl(authorizationUriTemplate)
							.buildAndExpand(uriVariables)
							.toUriString();

		return authorizationUri;
	}
	
	private String getShopName(HttpServletRequest request) {
		String shopName = request.getParameter(TokenService.SHOP_ATTRIBUTE_NAME);
		
		if(shopName == null || shopName.isEmpty()) {
			return null;
		}
		
		return shopName;
	}
	
	private boolean isAuthenticated(HttpServletRequest request) {
		if(SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken) {
			return true;
		}
		
		return false;
	}
	
	// return an OAuth2AuthorizationRequest so OAuth2AuthorizationRequestRedirectFilter
	// will redirect
	private OAuth2AuthorizationRequest redirectToLogin() {
		// clear all authentication
		if(SecurityContextHolder.getContext().getAuthentication() != null) {
			SecurityContextHolder.getContext().setAuthentication(null);
		}
		
		
		// The grant type cannot be AUTHORIZATION_CODE, since we don't want the 
		// OAuth2AuthorizationRequest saved in the session just yet
		OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.implicit()
				.authorizationUri("REDIRECT")
				.authorizationRequestUri(this.loginUri) // the redirect uri
				.clientId("REDIRECT")
				.redirectUri("REDIRECT")
				.build();

		return request;
				
				
	}
	

}
