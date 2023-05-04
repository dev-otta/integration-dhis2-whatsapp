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
package org.hisp.dhis.integration.rapidpro.processor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.hisp.dhis.api.model.v2_38_1.Attribute__2;
import org.hisp.dhis.api.model.v2_38_1.TrackedEntity;
import org.hisp.dhis.api.model.v2_38_1.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.datasonnet.document.DefaultDocument;
import com.datasonnet.document.Document;
import com.datasonnet.document.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ExistingEnrollmentEnumerator implements Processor
{
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void process( Exchange exchange )
        throws Exception
    {
        Map<String, Document<Map<String, Object>>> updatedDhis2Enrollments = new HashMap<>();
        List<TrackedEntity> dhis2Enrollments = exchange.getProperty( "dhis2Enrollments", List.class );
        Map<String, Object> rapidProContacts = exchange.getProperty( "rapidProContacts", Map.class );
        List<Map<String, Object>> results = (List<Map<String, Object>>) rapidProContacts.get( "results" );

        for ( TrackedEntity dhis2Enrollment : dhis2Enrollments )
        {
            Optional<Map<String, Object>> rapidProContact = results.stream().filter(
                c -> ((Map<String, Object>) c.get( "fields" )).get( "dhis2_enrollment_id" )
                    .equals( dhis2Enrollment.getEnrollments().get().get(0).getEnrollment().get() ) )
                .findFirst();

            rapidProContact.ifPresent( c -> updatedDhis2Enrollments.put( (String) c.get( "uuid" ),
                new DefaultDocument<>( objectMapper.convertValue( extractData(dhis2Enrollment), Map.class ),
                    new MediaType( "application", "x-java-object" ) ) ) );
        }
        exchange.getMessage().setBody( updatedDhis2Enrollments );
    }
    
    private Map<String, Object> extractData(TrackedEntity tei) {
        Map<String, Object> newEnrollmentContact = new HashMap<String,Object>();
        newEnrollmentContact.put("enrollmentId",tei.getEnrollments().get().get(0).getEnrollment().get());
        newEnrollmentContact.put("orgUnit",tei.getOrgUnit().get());
        newEnrollmentContact.put("createdAt", tei.getCreatedAt().get());
        newEnrollmentContact.put("trackedEntityInstance", tei.getTrackedEntity().get());
        newEnrollmentContact.put("whatsApp",extractWhatsAppNumber(tei.getAttributes().get()));
        return newEnrollmentContact;
    }
    private String extractWhatsAppNumber(List<Attribute__2> attributes) {
        for ( Attribute__2 attribute : attributes ) {
            if (attribute.getAttribute().get().contains("Ggv5SazHB4y")) {
                return attribute.getValue().get();
            }
        }
        return null;
    }
}

