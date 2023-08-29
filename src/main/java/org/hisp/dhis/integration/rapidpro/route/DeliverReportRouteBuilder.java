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

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.hisp.dhis.integration.rapidpro.CompleteDataSetRegistrationFunction;
import org.hisp.dhis.integration.rapidpro.ContactOrgUnitIdAggrStrategy;
import org.hisp.dhis.integration.rapidpro.EnrollmentAggrStrategy;
import org.hisp.dhis.integration.rapidpro.EventIdAggrStrategy;
import org.hisp.dhis.integration.rapidpro.ProgramStageDataElementsAggrStrategy;
import org.hisp.dhis.integration.rapidpro.expression.RootCauseExpr;
import org.hisp.dhis.integration.rapidpro.processor.CurrentPeriodCalculator;
import org.hisp.dhis.integration.rapidpro.processor.IdSchemeQueryParamSetter;
import org.hisp.dhis.integration.rapidpro.processor.EventIdFetcherQueryParamSetter;
import org.hisp.dhis.integration.rapidpro.processor.EventIdUpdateQueryParamSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class DeliverReportRouteBuilder extends AbstractRouteBuilder {
    @Autowired
    private CurrentPeriodCalculator currentPeriodCalculator;

    @Autowired
    private RootCauseExpr rootCauseExpr;

    @Autowired
    private IdSchemeQueryParamSetter idSchemeQueryParamSetter;

    @Autowired
    private EventIdFetcherQueryParamSetter eventIdFetcherQueryParamSetter;

    @Autowired
    private EventIdUpdateQueryParamSetter eventIdUpdateQueryParamSetter;

    @Autowired
    private ContactOrgUnitIdAggrStrategy contactOrgUnitIdAggrStrategy;

    @Autowired
    private EnrollmentAggrStrategy enrollmentAggrStrategy;

    @Autowired
    private EventIdAggrStrategy eventIdAggrStrategy;

    @Autowired
    private ProgramStageDataElementsAggrStrategy programStageDataElementsAggrStrategy;

    @Autowired
    private CompleteDataSetRegistrationFunction completeDataSetRegistrationFunction;

    @Override
    protected void doConfigure() {
        ErrorHandlerFactory errorHandlerDefinition = deadLetterChannel(
                "direct:dlq").maximumRedeliveries(3).useExponentialBackOff().useCollisionAvoidance()
                .allowRedeliveryWhileStopping(false);

        from("timer://retry?fixedRate=true&period=5000")
                .routeId("Retry Reports")
                .setBody(simple("${properties:retry.dlc.select.{{spring.sql.init.platform}}}"))
                .to("jdbc:dataSource")
                .split().body()
                .setHeader("id", simple("${body['id']}"))
                .log(LoggingLevel.INFO, LOGGER, "Retrying row with ID ${header.id}")
                .setHeader("dataSetCode", simple("${body['data_set_code']}"))
                .setHeader("reportPeriodOffset", simple("${body['report_period_offset']}"))
                .setHeader("orgUnitId", simple("${body['organisation_unit_id']}"))
                .setBody(simple("${body['payload']}"))
                .to("jms:queue:dhis2?exchangePattern=InOnly")
                .setBody(simple("${properties:processed.dlc.update.{{spring.sql.init.platform}}}"))
                .to("jdbc:dataSource?useHeadersAsParameters=true")
                .end();

        from("quartz://dhis2?cron={{report.delivery.schedule.expression}}")
                .routeId("Schedule Report Delivery")
                .precondition("'{{report.delivery.schedule.expression:}}' != ''")
                .pollEnrich("jms:queue:dhis2")
                .to("direct:deliverAggregateReport");

        from("jms:queue:dhis2")
                .routeId("Consume Report")
                .precondition("'{{report.delivery.schedule.expression:}}' == ''")
                .choice().when(header("reportType").isEqualTo("aggregate"))
                .to("direct:deliverAggregateReport")
                .otherwise()
                .to("direct:deliverEventReport");

        from("direct:deliverAggregateReport")
                .routeId("Deliver Aggregate Report")
                .to("direct:transformAggregateReport")
                .to("direct:transmitAggregateReport");

        from("direct:transformAggregateReport")
                .routeId("Transform Aggregate Report")
                .errorHandler(errorHandlerDefinition)
                .streamCaching()
                .setHeader("originalPayload", simple("${body}"))
                .unmarshal().json()
                .choice().when(header("reportPeriodOffset").isNull())
                .setHeader("reportPeriodOffset", constant(-1))
                .end()
                .enrich()
                .simple("dhis2://get/resource?path=dataElements&filter=dataSetElements.dataSet.code:eq:${headers['dataSetCode']}&fields=code&client=#dhis2Client")
                .aggregationStrategy((oldExchange, newExchange) -> {
                    oldExchange.getMessage().setHeader("dataElementCodes",
                            jsonpath("$.dataElements..code").evaluate(newExchange, List.class));
                    return oldExchange;
                })
                .choice().when(header("orgUnitId").isNull())
                .setHeader("Authorization", constant("Token {{rapidpro.api.token}}"))
                .enrich().simple("{{rapidpro.api.url}}/contacts.json?uuid=${body[contact][uuid]}&httpMethod=GET")
                .aggregationStrategy(contactOrgUnitIdAggrStrategy)
                .end()
                .removeHeader("Authorization")
                .end()
                .enrich("direct:computePeriod", (oldExchange, newExchange) -> {
                    oldExchange.getMessage().setHeader("period", newExchange.getMessage().getBody());
                    return oldExchange;
                })
                .transform(datasonnet("resource:classpath:dataValueSet.ds", Map.class, "application/x-java-object",
                        "application/x-java-object"))
                .process(idSchemeQueryParamSetter)
                .marshal().json().transform().body(String.class);

        from("direct:transmitAggregateReport")
                .routeId("Transmit Report")
                .errorHandler(errorHandlerDefinition)
                .log(LoggingLevel.INFO, LOGGER, "Saving data value set => ${body}")
                .setHeader("dhisRequest", simple("${body}"))
                .toD("dhis2://post/resource?path=dataValueSets&inBody=resource&client=#dhis2Client")
                .setBody((Function<Exchange, Object>) exchange -> exchange.getMessage().getBody(String.class))
                .setHeader("dhisResponse", simple("${body}"))
                .unmarshal().json()
                .choice()
                .when(simple("${body['status']} == 'SUCCESS' || ${body['status']} == 'OK'"))
                .to("direct:completeDataSetRegistration")
                .otherwise()
                .log(LoggingLevel.ERROR, LOGGER, "Import error from DHIS2 while saving data value set => ${body}")
                .to("direct:dlq")
                .end();

        from("direct:deliverEventReport")
                .routeId("Deliver Event Report")
                .to("direct:transformEventReport")
                .to("direct:transmitEventReport");

        from("direct:transformEventReport")
                .routeId("Transform Event Report")
                .streamCaching()
                .setHeader("originalPayload", simple("${body}"))
                .unmarshal().json()
                .setHeader("Authorization", constant("Token {{rapidpro.api.token}}"))
                .enrich().simple("{{rapidpro.api.url}}/contacts.json?uuid=${body[contact][uuid]}&httpMethod=GET")
                .aggregationStrategy(enrollmentAggrStrategy)
                .end()
                .removeHeader("Authorization")
                .end()
                .process(eventIdFetcherQueryParamSetter)
                .enrich()
                .simple("dhis2://get/resource?path=tracker/events&fields=event&client=#dhis2Client")
                .aggregationStrategy(eventIdAggrStrategy)
                .log(LoggingLevel.INFO, LOGGER, "Headers before transformation => ${headers}")
                .end()
                .removeHeader("CamelDhis2.queryParams")
                .enrich()
                .simple("dhis2://get/resource?path=programStages/${headers[programStageId]}&fields=programStageDataElements[dataElement[code]]&client=#dhis2Client")
                .aggregationStrategy(programStageDataElementsAggrStrategy)
                .end()
                .transform(datasonnet("resource:classpath:event.ds", Map.class, "application/x-java-object",
                        "application/x-java-object"))
                .process(eventIdUpdateQueryParamSetter)
                .marshal().json().transform().body(String.class);


        from("direct:transmitEventReport")
                .routeId("Transmit Event Report")
                // Todo
                // Identify the correct post uri for the DHIS2 component. (Event Update)
                // When successfull: log successfully updated program stage: header.dhis2request
                .errorHandler(errorHandlerDefinition)
                .log(LoggingLevel.INFO, LOGGER, "Updating program stage event => ${body}")
                .setHeader("dhisRequest", simple("${body}"))
                .toD("dhis2://post/resource?path=tracker&inBody=resource&client=#dhis2Client")
                .setBody((Function<Exchange, Object>) exchange -> exchange.getMessage().getBody(String.class))
                .setHeader("dhisResponse", simple("${body}"))
                .unmarshal().json()
                .choice()
                .when(simple("${body['status']} == 'SUCCESS' || ${body['status']} == 'OK'"))
                .log(LoggingLevel.INFO, LOGGER, "Success!")
                .otherwise()
                .log(LoggingLevel.ERROR, LOGGER, "Import error from DHIS2 while saving data value set => ${body}")
                //.to( "direct:dlq" )
                .end();
        from("direct:dlq")
                .routeId("Save Failed Report")
                .setHeader("errorMessage", rootCauseExpr)
                .setHeader("payload", header("originalPayload"))
                .setHeader("orgUnitId").ognl("request.headers.orgUnitId")
                .setHeader("dataSetCode").ognl("request.headers.dataSetCode")
                .setBody(simple("${properties:error.dlc.insert.{{spring.sql.init.platform}}}"))
                .to("jdbc:dataSource?useHeadersAsParameters=true");

        from("direct:computePeriod")
                .routeId("Compute Period")
                .toD("dhis2://get/collection?path=dataSets&filter=code:eq:${headers['dataSetCode']}&fields=periodType&itemType=org.hisp.dhis.api.model.v2_38_1.DataSet&paging=false&client=#dhis2Client")
                .process(currentPeriodCalculator);

        from("direct:completeDataSetRegistration")
                .setBody(completeDataSetRegistrationFunction)
                .toD("dhis2://post/resource?path=completeDataSetRegistrations&inBody=resource&client=#dhis2Client")
                .unmarshal().json()
                .choice()
                .when(simple("${body['status']} == 'SUCCESS' || ${body['status']} == 'OK'"))
                .setHeader("rapidProPayload", header("originalPayload"))
                .setBody(simple("${properties:success.log.insert.{{spring.sql.init.platform}}}"))
                .to("jdbc:dataSource?useHeadersAsParameters=true")
                .otherwise()
                .log(LoggingLevel.ERROR, LOGGER, "Error from DHIS2 while completing data set registration => ${body}")
                .to("direct:dlq")
                .end();
    }
}
