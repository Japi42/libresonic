package org.libresonic.player.boot;

import net.sf.ehcache.constructs.web.ShutdownListener;
import org.apache.catalina.Container;
import org.apache.catalina.Wrapper;
import org.apache.catalina.webresources.StandardRoot;
import org.directwebremoting.servlet.DwrServlet;
import org.libresonic.player.filter.*;
import org.libresonic.player.spring.LibresonicPropertySourceConfigurer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatContextCustomizer;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

import javax.servlet.Filter;
import javax.servlet.ServletContextListener;

@SpringBootApplication(exclude = {
        JmxAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class,
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        LiquibaseAutoConfiguration.class})
@Configuration
@ImportResource(value = {"classpath:/applicationContext-service.xml",
        "classpath:/applicationContext-cache.xml",
        "classpath:/applicationContext-sonos.xml",
        "classpath:/libresonic-servlet.xml"})
public class Application extends SpringBootServletInitializer implements EmbeddedServletContainerCustomizer {

    /**
     * Registers the DWR servlet.
     *
     * @return a registration bean.
     */
    @Bean
    public ServletRegistrationBean dwrServletRegistrationBean() {
        ServletRegistrationBean servlet = new ServletRegistrationBean(new DwrServlet(), "/dwr/*");
        servlet.addInitParameter("crossDomainSessionSecurity","false");
        return servlet;
    }

    @Bean
    public ServletRegistrationBean cxfServletBean() {
        return new ServletRegistrationBean(new org.apache.cxf.transport.servlet.CXFServlet(), "/ws/*");
    }

    @Bean
    public ServletContextListener ehCacheShutdownListener() {
        return new ShutdownListener();
    }

    @Bean
    public FilterRegistrationBean bootstrapVerificationFilterRegistration() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(bootstrapVerificationFiler());
        registration.addUrlPatterns("/*");
        registration.setName("BootstrapVerificationFilter");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public Filter bootstrapVerificationFiler() {
        return new BootstrapVerificationFilter();
    }

    @Bean
    public FilterRegistrationBean parameterDecodingFilterRegistration() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(parameterDecodingFilter());
        registration.addUrlPatterns("/*");
        registration.setName("ParameterDecodingFilter");
        registration.setOrder(2);
        return registration;
    }

    @Bean
    public Filter parameterDecodingFilter() {
        return new ParameterDecodingFilter();
    }

    @Bean
    public FilterRegistrationBean restFilterRegistration() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(restFilter());
        registration.addUrlPatterns("/rest/*");
        registration.setName("RESTFilter");
        registration.setOrder(3);
        return registration;
    }

    @Bean
    public Filter restFilter() {
        return new RESTFilter();
    }

    @Bean
    public FilterRegistrationBean requestEncodingFilterRegistration() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(requestEncodingFilter());
        registration.addUrlPatterns("/*");
        registration.addInitParameter("encoding", "UTF-8");
        registration.setName("RequestEncodingFilter");
        registration.setOrder(4);
        return registration;
    }

    @Bean
    public Filter requestEncodingFilter() {
        return new RequestEncodingFilter();
    }

    @Bean
    public FilterRegistrationBean cacheFilterRegistration() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(cacheFilter());
        registration.addUrlPatterns("/icons/*", "/style/*");
        registration.addInitParameter("Cache-Control", "max-age=36000");
        registration.setName("CacheFilter");
        registration.setOrder(5);
        return registration;
    }

    @Bean
    public Filter cacheFilter() {
        return new ResponseHeaderFilter();
    }

    @Bean
    public FilterRegistrationBean noCacheFilterRegistration() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(noCacheFilter());
        registration.addUrlPatterns("/statusChart.view", "/userChart.view", "/playQueue.view", "/podcastChannels.view", "/podcastChannel.view", "/help.view", "/top.view", "/home.view");
        registration.addInitParameter("Cache-Control", "no-cache, post-check=0, pre-check=0");
        registration.addInitParameter("Pragma", "no-cache");
        registration.addInitParameter("Expires", "Thu, 01 Dec 1994 16:00:00 GMT");
        registration.setName("NoCacheFilter");
        registration.setOrder(6);
        return registration;
    }

    @Bean
    public Filter noCacheFilter() {
        return new ResponseHeaderFilter();
    }

    private static SpringApplicationBuilder doConfigure(SpringApplicationBuilder application) {
        // Customize the application or call application.sources(...) to add sources
        // Since our example is itself a @Configuration class (via @SpringBootApplication)
        // we actually don't need to override this method.
        return application.sources(Application.class).web(true).initializers(new LibresonicPropertySourceConfigurer());
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return doConfigure(application);
    }

    @Override
    public void customize(ConfigurableEmbeddedServletContainer container) {
        if (container instanceof TomcatEmbeddedServletContainerFactory) {
            TomcatEmbeddedServletContainerFactory tomcatFactory = (TomcatEmbeddedServletContainerFactory) container;
            tomcatFactory.addContextCustomizers((TomcatContextCustomizer) context -> {

                // Increase the size and time before eviction of the Tomcat
                // cache so that resources aren't uncompressed too often.
                // See https://github.com/jhipster/generator-jhipster/issues/3995
                StandardRoot resources = new StandardRoot();
                resources.setCacheMaxSize(100000);
                resources.setCacheObjectMaxSize(4000);
                resources.setCacheTtl(24 * 3600 * 1000);  // 1 day, in milliseconds
                context.setResources(resources);

                // Put Jasper in production mode so that JSP aren't recompiled
                // on each request.
                // See http://stackoverflow.com/questions/29653326/spring-boot-application-slow-because-of-jsp-compilation
                Container jsp = context.findChild("jsp");
                if (jsp instanceof Wrapper) {
                    ((Wrapper)jsp).addInitParameter("development", "false");
                }
            });
        }

    }

    public static void main(String[] args) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder();
        doConfigure(builder).run(args);
    }

}
