package com.lm.security.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import com.lm.security.authentication.CipherPassword;
import com.lm.security.authentication.ShopifyVerificationStrategy;
import com.lm.security.service.DefaultShopifyUserService;
import com.lm.security.service.ShopifyOAuth2AuthorizedClientService;
import com.lm.security.service.TokenService;
import com.lm.security.web.NoRedirectSuccessHandler;
import com.lm.security.web.ShopifyAuthorizationCodeTokenResponseClient;
import com.lm.security.web.ShopifyHttpSessionOAuth2AuthorizationRequestRepository;
import com.lm.security.web.ShopifyOAuth2AuthorizationRequestResolver;


@Configuration
public class SecurityBeansConfig {
	
	@Autowired
	private TokenService tokenService;
	
	@Bean
	CipherPassword cipherPassword(@Value("${lm.security.cipher.password}") String password) {
		return new CipherPassword(password);
	}
	
	
	@Bean
	OAuth2UserService<OAuth2UserRequest, OAuth2User> userService() {
		return new DefaultShopifyUserService();
	}
	
	
	@Bean
	public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient() {
		return new ShopifyAuthorizationCodeTokenResponseClient();
	}
	
	
	@Bean
	public AuthenticationSuccessHandler successHandler() {
		return new NoRedirectSuccessHandler();
	}
	
	
	@Bean
    public ClientRegistrationRepository clientRegistrationRepository(ClientRegistration shopifyClientRegistration) {
        return new InMemoryClientRegistrationRepository(shopifyClientRegistration);
    }
	
	// used by AuthenticatedPrincipalOAuth2AuthorizedClientRepository
	@Bean
	public OAuth2AuthorizedClientService clientService() {
		return new ShopifyOAuth2AuthorizedClientService(this.tokenService);
	}
	
	@Bean
	public OAuth2AuthorizationRequestResolver shopifyOauth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
		return new ShopifyOAuth2AuthorizationRequestResolver(clientRegistrationRepository, customAuthorizationRequestRepository(), SecurityConfig.INSTALL_PATH);
	}
	
	@Bean
	public ShopifyHttpSessionOAuth2AuthorizationRequestRepository customAuthorizationRequestRepository() {
		return new ShopifyHttpSessionOAuth2AuthorizationRequestRepository();
	}
	

	@Bean
	protected ClientRegistration shopifyClientRegistration(@Value("${shopify.client.client_id}")String clientId,
			 @Value("${shopify.client.client_secret}")String clientSecret, 
			 @Value("${shopify.client.scope}")String scope) {
		

        return ClientRegistration.withRegistrationId("shopify")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUriTemplate("{baseUrl}" + SecurityConfig.AUTHORIZATION_REDIRECT_PATH + "{registrationId}")
            .scope(scope.split(","))
            .authorizationUri("https://{shop}/admin/osauth/authorize")
            .tokenUri("https://{shop}/admin/oauth/access_token")
            .clientName("Shopify")
            .build();
    }
	
	@Bean
	public ShopifyVerificationStrategy shopifyVerficationStrategy() {
		return new ShopifyVerificationStrategy(customAuthorizationRequestRepository());
	}

}
