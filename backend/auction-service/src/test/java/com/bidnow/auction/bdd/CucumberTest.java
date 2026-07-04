// backend/auction-service/src/test/java/com/bidnow/auction/bdd/CucumberTest.java
package com.bidnow.auction.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ConfigurationParameters;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameters({
        @ConfigurationParameter(
                key = PLUGIN_PROPERTY_NAME,
                value = "pretty, html:target/cucumber-reports/report.html"
        ),
        @ConfigurationParameter(
                key = GLUE_PROPERTY_NAME,
                value = "com.bidnow.auction.bdd"
        )
})
public class CucumberTest {
}
