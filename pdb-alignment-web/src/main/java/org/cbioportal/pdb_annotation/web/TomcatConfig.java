    package org.cbioportal.pdb_annotation.web;
    
    import org.apache.catalina.connector.Connector;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
    import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
    import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    
    /**
     * Used for support for both http and https
     * If want to use both http and https:
     * 1.Unannotate TomcatConfig.class
     * 2.use http.port=8080 in application.properties
     * 3.Annotate the bottom part of Application.class
     * 
     * Else if want to mapping http to https:
     * 1. Annotate TomcatConfig.class
     * 2. Annotate use http.port=8080 in application.properties
     * 3. Unannotate the bottom part of Application.class
     * 
     * @author wangjue
     *
     */
    @Configuration
    public class TomcatConfig {
        /*
        @Value("${http.port}")
        private int httpPort;
    
        @Bean
        public EmbeddedServletContainerCustomizer containerCustomizer() {
            return new EmbeddedServletContainerCustomizer() {
                @Override
                public void customize(ConfigurableEmbeddedServletContainer container) {
                    if (container instanceof TomcatEmbeddedServletContainerFactory) {
                        TomcatEmbeddedServletContainerFactory containerFactory =
                                (TomcatEmbeddedServletContainerFactory) container;
    
                        Connector connector = new Connector(TomcatEmbeddedServletContainerFactory.DEFAULT_PROTOCOL);
                        connector.setPort(httpPort);
                        containerFactory.addAdditionalTomcatConnectors(connector);
                    }
                }
            };
        }
    */
    }
