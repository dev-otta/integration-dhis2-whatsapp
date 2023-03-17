package org.hisp.dhis.integration.rapidpro.processor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.hisp.dhis.api.model.v2_38_1.TrackedEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.datasonnet.document.DefaultDocument;
import com.datasonnet.document.Document;
import com.datasonnet.document.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class NewEnrollmentEnumerator implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Set<Document<Map<String, Object>>> newDhis2Enrollments = new HashSet<>();
        List<TrackedEntity> dhis2Enrollments = exchange.getProperty( "dhis2Enrollments", List.class );
        Map<String, Object> rapidProContacts = exchange.getProperty( "rapidProContacts", Map.class );
        List<Map<String, Object>> results = (List<Map<String, Object>>) rapidProContacts.get( "results" );

        for ( TrackedEntity dhis2Enrollment : dhis2Enrollments )
        {
            if ( isNotBlank( dhis2Enrollment.getTrackedEntity() ) )
            {
                Optional<Map<String, Object>> rapidProContact = results.stream().filter(
                        c -> ((Map<String, Object>) c.get( "fields" )).get( "dhis2_user_id" )
                            .equals( dhis2User.getId().get() ) )
                    .findFirst();

                if ( rapidProContact.isEmpty() )
                {
                    newDhis2Enrollments.add( new DefaultDocument<>( objectMapper.convertValue( dhis2User, Map.class ),
                        new MediaType( "application", "x-java-object" ) ) );
                }
            }
        }

        exchange.getMessage().setBody( newDhis2Enrollments );
    }

    private boolean isNotBlank( Optional<String> stringOptional )
    {
        return stringOptional.isPresent() && !stringOptional.get().isBlank();
    }
}
