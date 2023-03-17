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
import org.hisp.dhis.integration.rapidpro.processor.ExtractEnrollments;
@Component
public class EnrollmentSyncRouteBuilder extends AbstractRouteBuilder {
    @Autowired
    private ExtractEnrollments extractEnrollments;

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
        .log( LoggingLevel.INFO, LOGGER, "Synchronising DHIS2 tracked entities with RapidPro contacts..." )
        .to( "direct:prepareTrackerRapidPro" )
        .setHeader("CamelDhis2.queryParams",constant(Map.of("program","JA1vLYT8htI","orgUnit","DFn0SHhghQp","skipPaging","true")))
        .setProperty( "orgUnitIdScheme", simple( "{{org.unit.id.scheme}}" ) )
        .toD( "dhis2://get/resource?path=tracker/trackedEntities&fields=trackedEntity,enrollment,createdAt,attributes[code,attribute,value],enrollments[enrollment]&client=#dhis2Client")
        .log("Body before process ${body}")
        .process(extractEnrollments)
        .unmarshal().json(TrackedEntity[].class)
        .log("${body}");
    }
    
}