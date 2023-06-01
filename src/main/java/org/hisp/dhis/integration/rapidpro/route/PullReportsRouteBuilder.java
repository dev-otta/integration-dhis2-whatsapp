/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.integration.rapidpro.route;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.hisp.dhis.integration.rapidpro.expression.LastRunCalculator;
import org.hisp.dhis.integration.rapidpro.expression.LastRunAtColumnReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PullReportsRouteBuilder extends AbstractRouteBuilder
{
    @Autowired
    private LastRunCalculator lastRunCalculator;

    @Autowired
    private LastRunAtColumnReader lastRunAtColumnReader;

    @Override
    protected void doConfigure()
    {
        from( "servlet:tasks/scan?muteException=true" )
            .removeHeaders( "*" )
            .to( "direct:pull" )
            .setHeader( Exchange.CONTENT_TYPE, constant( "application/json" ) )
            .setBody( constant( Map.of("status", "success", "data", "Scanned RapidPro flow runs") ) )
            .marshal().json();

        from( "quartz://pull?cron={{scan.reports.schedule.expression:0 0/30 * * * ?}}&stateful=true" )
            .to( "direct:pull" );

        from( "direct:pull" )
            .routeId( "Scan RapidPro Flows" )
            .streamCaching()
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .split( simple( "{{rapidpro.flow.uuids:}}" ), "," )
                .setHeader( "flowUuid", simple( "${body}" ) )
                .setBody( simple( "${properties:last.run.select.{{spring.sql.init.platform}}}" ) )
                .to( "jdbc:dataSource?useHeadersAsParameters=true" )
                .setProperty( "lastRunAt", lastRunAtColumnReader )
                .setProperty( "nextRunsPageUrl", simple( "{{rapidpro.api.url}}/runs.json?flow=${header.flowUuid}&after=${exchangeProperty.lastRunAt}&reverse=true" ) )
                .setHeader( "newLastRunAt" ).ognl( "@java.sql.Timestamp@from(@java.time.Instant@now())" )
                .loopDoWhile( exchangeProperty( "nextRunsPageUrl" ).isNotNull() )
                    .toD( "${exchangeProperty.nextRunsPageUrl}" )
                    .log( LoggingLevel.DEBUG, LOGGER, "Fetched flow runs from ${exchangeProperty.nextRunsPageUrl} => ${body}" )
                    .unmarshal().json()
                    .setProperty( "nextRunsPageUrl", simple( "${body[next]}" ) )
                    .setHeader( "newLastRunAt", lastRunCalculator )
                    .split( simple( "${body[results]}" ) )
                        // Changelog: Added choice() EIP that seperates the flows based on if it is aggregate or tracker
                        .filter( simple( "${body[exited_on]} != null && ${body[exit_type]} == 'completed'" ) )
                        .choice()
                            .when( simple( "${body[values][data_set_code]} != null && ${body[values][program_id]} == null" ) )
                                .to("direct:aggregateFlow")
                            .when( simple("${body[values][program_id]} != null && ${body[values][data_set_code]} == null"))
                                .to("direct:trackerFlow")
                            .otherwise()
                                .log( LoggingLevel.ERROR, LOGGER, "Cannot process run for flow definition ${header.flowUuid} because some of the required fields are missing. Hint: For flows corresponding to aggregate reports, save the data set code to a flow result named `data_set_code` in RapidPro. For flows corresponding to program stage updates, save the program ID to a flow result named 'program_id in RapidPro.'" )
                        .end()
                    .end()
                .end()
                .setBody( simple( "${properties:last.run.upsert.{{spring.sql.init.platform}}}" ) )
                .to( "jdbc:dataSource?useHeadersAsParameters=true" )
            .end();
        from( "direct:aggregateFlow" )
            .log( LoggingLevel.DEBUG, LOGGER, "Handling aggregate flow..." )
            .setHeader( "reportType" ).simple( "aggregate" )
            .setHeader( "dataSetCode", simple( "${body[values][data_set_code][value]}" ) )
            .setHeader( "orgUnitId" ).ognl( "request.body['values']['org_unit_id'] == null ? null : request.body['values']['org_unit_id']['value']" )
            .setHeader( "reportPeriodOffset" ).ognl( "request.body['values']['report_period_offset'] == null ? null : request.body['values']['report_period_offset']['value']" )
            .transform( datasonnet( "resource:classpath:webhook.ds", String.class, "application/x-java-object", "application/json" ) )
            .to( "jms:queue:dhis2?exchangePattern=InOnly" )
            .log( LoggingLevel.DEBUG, LOGGER, "Enqueued aggregate flow run [data set code = ${header.dataSetCode},report period offset = ${header.reportPeriodOffset},content = ${body}]" )
            .end();
        from( "direct:trackerFlow" )
            .log( LoggingLevel.DEBUG, LOGGER, "Handling tracker flow..." )
            .setHeader( "reportType" ).simple("event")
            .setHeader( "programId", simple( "${body[values][program_id][value]}" ) )
            .setHeader( "programStageId",simple( "${body[values][program_stage_id][value]}" ))
            .setHeader( "orgUnitId" ).ognl( "request.body['values']['org_unit_id'] == null ? null : request.body['values']['org_unit_id']['value']" )
            .transform( datasonnet( "resource:classpath:webhook.ds", String.class, "application/x-java-object", "application/json" ) )
            .to( "jms:queue:dhis2?exchangePattern=InOnly" )
            .log( LoggingLevel.DEBUG, LOGGER, "Enqueued tracker event flow run [program ID = ${header.programId}, content = ${body}]" )
            .end();
    }   
}
