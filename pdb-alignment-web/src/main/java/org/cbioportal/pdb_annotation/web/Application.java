package org.cbioportal.pdb_annotation.web;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;

import springfox.documentation.builders.PathSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * 
 * @author Juexin Wang
 *
 */ 
@SpringBootApplication
@SpringBootConfiguration
@EnableSwagger2
public class Application extends SpringBootServletInitializer{

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Application.class);
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Application.class, args);
    }

    /*
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    */
    

    @Bean
    public Docket annotationApi() {
        // default swagger definition file location:
        // <root>/v2/api-docs?group=pdb_annotation
        // default swagger UI location: <root>/swagger-ui.html
        return new Docket(DocumentationType.SWAGGER_2).groupName("api").apiInfo(annotationApiInfo()).select()
                .paths(PathSelectors.regex("/api.*")).build();
    }

    private ApiInfo annotationApiInfo() {
        /*
         * return new ApiInfoBuilder().title("G2S API").description(
         * "A Genome to Strucure (G2S) API Supports Automated Mapping and Annotating Genomic Variants in 3D Protein Structures. Supports Inputs from Human Genome Position, Uniprot and Human Ensembl Names"
         * ) // .termsOfServiceUrl("http://terms-of-service-url")
         * .termsOfServiceUrl("http://g2s.genomenexus.org") .contact(
         * "CMO, MSKCC").license("GNU AFFERO GENERAL PUBLIC LICENSE Version 3")
         * .licenseUrl(
         * "https://github.com/cBioPortal/pdb-annotation/blob/master/LICENSE").
         * version("2.0").build();
         */
        ApiInfo apiInfo = new ApiInfo("G2S web API",
                "A Genome to Strucure (G2S) API Supports Automated Mapping and Annotating Genomic Variants in 3D Protein Structures. Supports Inputs from Human Genome Position, Uniprot and Human Ensembl Names.",
                "1.0 (beta)", "g2s.genomenexus.org",
                new Contact("G2S", "http://g2s.genomenexus.org", "wangjue@missouri.edu"), "License",
                "https://github.com/cBioPortal/cbioportal/blob/master/LICENSE");
        return apiInfo;
    }
    
    
  //http to https
    
    @Bean
    public EmbeddedServletContainerFactory servletContainer() {
      TomcatEmbeddedServletContainerFactory tomcat = new TomcatEmbeddedServletContainerFactory() {
          @Override
          protected void postProcessContext(Context context) {
            SecurityConstraint securityConstraint = new SecurityConstraint();
            securityConstraint.setUserConstraint("CONFIDENTIAL");
            SecurityCollection collection = new SecurityCollection();
            collection.addPattern("/*");
            securityConstraint.addCollection(collection);
            context.addConstraint(securityConstraint);
          }
        };
      
      tomcat.addAdditionalTomcatConnectors(initiateHttpConnector());
      return tomcat;
    }
    
    private Connector initiateHttpConnector() {
      Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
      connector.setScheme("http");
      connector.setPort(8080);
      connector.setSecure(false);
      connector.setRedirectPort(5443);
      
      return connector;
    }
    
}
