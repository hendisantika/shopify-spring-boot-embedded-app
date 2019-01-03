package com.lm.security.configuration;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutFilter;

import com.lm.security.authentication.ShopifyVerificationStrategy;
import com.lm.security.filters.BehindHttpsProxyFilter;
import com.lm.security.filters.ShopifyExistingTokenFilter;
import com.lm.security.filters.ShopifyOriginFilter;
import com.lm.security.service.TokenService;


@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
	
	public static final String INSTALL_PATH = "/install";
	public static final String ANY_INSTALL_PATH = INSTALL_PATH + "/**";
	public static final String AUTHORIZATION_REDIRECT_PATH = "/login/app/oauth2/code";
	public static final String ANY_AUTHORIZATION_REDIRECT_PATH = AUTHORIZATION_REDIRECT_PATH + "/**";
	public static final String LOGIN_ENDPOINT = "/init";
	public static final String LOGOUT_ENDPOINT = "/logout";
	public static final String AUTHENTICATION_FALURE_URL = "/auth/error";
	public static final String UNINSTALL_URI = "/store/uninstall";
	
	
	@Autowired
	ApplicationContext ctx;
	
	@Autowired
	private TokenService tokenService;
	
	@Autowired
	private ShopifyVerificationStrategy shopifyVerficationStrategy;
	
	@Autowired
	private OAuth2AuthorizationRequestResolver shopifyOauth2AuthorizationRequestResolver;
	
	@Autowired
	OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> accessTokenResponseClient;
	
	@Autowired
	OAuth2UserService<OAuth2UserRequest, OAuth2User> userService;
	
	@Autowired
	AuthenticationSuccessHandler successHandler;
	
	
	@Override
	protected void configure(HttpSecurity http) throws Exception {
						
		http.addFilterAfter(new ShopifyOriginFilter(shopifyVerficationStrategy, ANY_AUTHORIZATION_REDIRECT_PATH, ANY_INSTALL_PATH), LogoutFilter.class);
		http.addFilterAfter(new ShopifyExistingTokenFilter(this.tokenService, ANY_INSTALL_PATH), ShopifyOriginFilter.class);
		http.addFilterBefore(new BehindHttpsProxyFilter(ANY_AUTHORIZATION_REDIRECT_PATH, ANY_INSTALL_PATH), OAuth2AuthorizationRequestRedirectFilter.class);

		http.headers().frameOptions().disable()
			  .and()
	          .authorizeRequests()
	          	.mvcMatchers(LOGIN_ENDPOINT).permitAll()
	          	.mvcMatchers("/favicon.ico").permitAll()
	          	.anyRequest().authenticated()
	          .and()
	          .logout()
	          	.logoutUrl(LOGOUT_ENDPOINT)
	          	.logoutSuccessUrl(LOGIN_ENDPOINT)
	          .and()
	          .oauth2Login()
	          	.authorizationEndpoint()
	          		.authorizationRequestResolver(shopifyOauth2AuthorizationRequestResolver)
	          .and()
	          	.redirectionEndpoint().baseUri(ANY_AUTHORIZATION_REDIRECT_PATH) // same as filterProcessesUrl
	          .and()
	          	.tokenEndpoint().accessTokenResponseClient(accessTokenResponseClient) // allows for seamless unit testing
	          .and()
	          	.userInfoEndpoint().userService(userService)
	          .and()
	          	.successHandler(successHandler)
	          	.loginPage(LOGIN_ENDPOINT) // for use outside of an embedded app since it involves a redirect
	          	.failureUrl(AUTHENTICATION_FALURE_URL); // see AbstractAuthenticationProcessingFilter
		

		          
	}
	

}


