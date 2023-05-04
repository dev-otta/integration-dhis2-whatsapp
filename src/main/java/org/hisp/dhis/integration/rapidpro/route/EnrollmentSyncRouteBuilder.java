package org.hisp.dhis.integration.rapidpro.route;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.hisp.dhis.api.model.v2_38_1.TrackedEntity;
import org.hisp.dhis.integration.rapidpro.expression.IterableReader;
import org.hisp.dhis.integration.rapidpro.processor.ExistingEnrollmentEnumerator;
import org.hisp.dhis.integration.rapidpro.processor.ExtractEnrollments;
import org.hisp.dhis.integration.rapidpro.processor.NewEnrollmentEnumerator;
@Component
public class EnrollmentSyncRouteBuilder extends AbstractRouteBuilder {
    @Autowired
    private ExtractEnrollments extractEnrollments;

    @Autowired
    private NewEnrollmentEnumerator NewEnrollmentEnumerator;

    @Autowired
    private ExistingEnrollmentEnumerator existingEnrollmentEnumerator;

    @Autowired
    private IterableReader iterableReader;

    @Override
    protected void doConfigure() throws Exception {
        from( "servlet:tasks/enrollmentSync?muteException=true" )
            .precondition( "{{sync.dhis2.enrollments}}" )
            .removeHeaders( "*" )
            .to("direct:enrollmentSync")
            .setHeader( Exchange.CONTENT_TYPE, constant( "application/json" ) )
            .setBody( constant( Map.of("status", "success", "data", "Synchronised RapidPro contacts with DHIS2 Enrollments") ) )
            .marshal().json();

        from( "quartz://enrollmentSync?cron={{sync.schedule.expression:0 0/30 * * * ?}}&stateful=true" )
            .precondition( "{{sync.dhis2.enrollments}}" )
            .to( "direct:enrollmentSync" );

        from("direct:enrollmentSync")
        .precondition( "{{sync.dhis2.enrollments}}" )
        .routeId("Enrollment Sync")
        .log( LoggingLevel.INFO, LOGGER, "Synchronising {{dhis2.program.name}} enrollments with RapidPro contacts..." )
        .to( "direct:prepareTrackerRapidPro" )
        .setHeader("CamelDhis2.queryParams",constant(Map.of("program","JA1vLYT8htI","orgUnit","DFn0SHhghQp","skipPaging","true")))
        .setProperty( "orgUnitIdScheme", simple( "{{org.unit.id.scheme}}" ) )
        // TODO: Limit data returned from api request. 
        .toD( "dhis2://get/resource?path=tracker/trackedEntities&fields=trackedEntity,orgUnit,enrollment,createdAt,attributes[code,attribute,value],enrollments[enrollment]&client=#dhis2Client")
        .process(extractEnrollments)
        .unmarshal().json(TrackedEntity[].class)
        .setProperty( "dhis2Enrollments", iterableReader)
        .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .setProperty( "nextContactsPageUrl", simple( "{{rapidpro.api.url}}/contacts.json?group={{dhis2.program.name}}" ) )
            .loopDoWhile( exchangeProperty( "nextContactsPageUrl" ).isNotNull() )
                .toD( "${exchangeProperty.nextContactsPageUrl}" ).unmarshal().json()
                .setProperty( "nextContactsPageUrl", simple( "${body[next]}" ) )
                .setProperty( "rapidProContacts", simple( "${body}" ) )
                .process( NewEnrollmentEnumerator )
                .split().body()
                    .to( "direct:createEnrollmentContact" )
                .end()
                .process( existingEnrollmentEnumerator )
                .split().body()
                    .to( "direct:updateEnrollmentContact" )
                .end()
            .end()
            .log( LoggingLevel.INFO, LOGGER, "Completed synchronisation of RapidPro contacts with {{dhis2.program.name}} enrollments" );

        from( "direct:createEnrollmentContact" )
            .transform( datasonnet( "resource:classpath:enrollmentContact.ds", Map.class, "application/x-java-object", "application/x-java-object" ) )
            .setProperty( "dhis2EnrollmentId", simple( "${body['fields']['dhis2_enrollment_id']}" ) )
            .marshal().json().convertBodyTo( String.class )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .log( LoggingLevel.DEBUG, LOGGER, "Creating RapidPro contact for {{dhis2.program.name}} enrollment ${exchangeProperty.dhis2EnrollmentId}" )
            .toD( "{{rapidpro.api.url}}/contacts.json?httpMethod=POST&okStatusCodeRange=200-499" )
            .choice().when( header( Exchange.HTTP_RESPONSE_CODE ).isNotEqualTo( "201" ) )
                .log( LoggingLevel.WARN, LOGGER, "Unexpected status code when creating RapidPro contact for DHIS2 enrollment ${exchangeProperty.dhis2EnrollmentId} => HTTP ${header.CamelHttpResponseCode}. HTTP response body => ${body}" )
            .end();

        from( "direct:updateEnrollmentContact" )
            .setProperty( "rapidProUuid", simple( "${body.getKey}" ) )
            .setBody( simple( "${body.getValue}" ) )
            .transform( datasonnet( "resource:classpath:enrollmentContact.ds", Map.class, "application/x-java-object", "application/x-java-object" ) )
            .marshal().json().convertBodyTo( String.class )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .log( LoggingLevel.DEBUG, LOGGER, "Updating RapidPro contact ${exchangeProperty.rapidProUuid}" )
            .toD( "{{rapidpro.api.url}}/contacts.json?uuid=${exchangeProperty.rapidProUuid}&httpMethod=POST&okStatusCodeRange=200-499" )
            .choice().when( header( Exchange.HTTP_RESPONSE_CODE ).isNotEqualTo( "200" ) )
                .log( LoggingLevel.WARN, LOGGER, "Unexpected status code when updating RapidPro contact ${exchangeProperty.rapidProUuid} => HTTP ${header.CamelHttpResponseCode}. HTTP response body => ${body}" )
            .end();

    }
}