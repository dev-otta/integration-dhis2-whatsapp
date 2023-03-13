package org.hisp.dhis.integration.rapidpro.route;

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

import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SetUpTrackerRapidProRoutebuilder extends AbstractRouteBuilder
{
    @Value("${dhis2.program.name}")
    protected String programName;
    @Override
    protected void doConfigure()
    {
        from( "direct:prepareTrackerRapidPro" ).routeId( "Set up Program Fields RapidPro" ).to( "direct:createProgramFieldsRoute" )
                                                    .to( "direct:createProgramGroupRoute" );

        setUpCreateProgramFieldsRoute();
        setUpCreateProgramGroupRoute();
    }

    private void setUpCreateProgramFieldsRoute()
    {
        from( "direct:createProgramFieldsRoute" ).routeId( "Create Program RapidPro Fields" )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .toD( "{{rapidpro.api.url}}/fields.json?key=dhis2_organisation_unit_id&httpMethod=GET" )
            .setProperty( "fieldCount", jsonpath( "$.results.length()" ) )
            .choice().when().simple( "${exchangeProperty.fieldCount} == 0" )
                .log( LoggingLevel.INFO, LOGGER, "Creating DHIS2 Organisation Unit ID fields in RapidPro..." )
                .setBody( constant( Map.of( "label", "DHIS2 Organisation Unit ID", "value_type", "text" ) ) ).marshal()
                .json().toD( "{{rapidpro.api.url}}/fields.json?httpMethod=POST" )
            .end()
            .toD( "{{rapidpro.api.url}}/fields.json?key=dhis2_enrollment_id&httpMethod=GET" )
            .setProperty( "fieldCount", jsonpath( "$.results.length()" ) )
            .choice().when().simple( "${exchangeProperty.fieldCount} == 0" )
                .log( LoggingLevel.INFO, LOGGER, "Creating DHIS2 Enrollment ID field in RapidPro..." )
                .setBody( constant( Map.of( "label", "DHIS2 Enrollment ID", "value_type", "text" ) ) ).marshal().json()
                .toD( "{{rapidpro.api.url}}/fields.json?httpMethod=POST" )
            .end()
            .toD( "{{rapidpro.api.url}}/fields.json?key=dhis2_enrolled_at&httpMethod=GET" )
            .setProperty( "fieldCount", jsonpath( "$.results.length()" ) )
            .choice().when().simple( "${exchangeProperty.fieldCount} == 0" )
                .log( LoggingLevel.INFO, LOGGER, "Creating DHIS2 Enrolled At field in RapidPro..." )
                .setBody( constant( Map.of( "label", "DHIS2 Enrolled At", "value_type", "text" ) ) ).marshal().json()
                .toD( "{{rapidpro.api.url}}/fields.json?httpMethod=POST" )
            .end();
    }

    private void setUpCreateProgramGroupRoute() 
    {
        from( "direct:createProgramGroupRoute" ).routeId( "Create Program RapidPro Group" )
            .setHeader( "Authorization", constant( "Token {{rapidpro.api.token}}" ) )
            .toD( "{{rapidpro.api.url}}/groups.json?name={{dhis2.program.name}}&httpMethod=GET" )
            .setProperty( "groupCount", jsonpath( "$.results.length()" ) )
            .choice().when()
                .simple( "${exchangeProperty.groupCount} == 0" ).log( LoggingLevel.INFO, LOGGER, "Creating {{dhis2.program.name}} group in RapidPro..." )
                // FIXME: remove programName variable and replace it with ${} notation
                .setBody( constant( Map.of( "name", programName) ) ).marshal()
                .json().toD( "{{rapidpro.api.url}}/groups.json?httpMethod=POST" ).setProperty( "groupUuid", jsonpath( "$.uuid" ) )
            .otherwise()
                .setProperty( "groupUuid", jsonpath( "$.results[0].uuid" ) );
    }
}
