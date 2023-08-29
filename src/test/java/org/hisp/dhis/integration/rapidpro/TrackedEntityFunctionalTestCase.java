package org.hisp.dhis.integration.rapidpro;


import org.hisp.dhis.api.model.v2_38_1.TrackedEntity;
import org.junit.jupiter.api.Test;


import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class TrackedEntityFunctionalTestCase extends AbstractFunctionalTestCase
{


    @Override
    public void doBeforeEach()
            throws
            IOException
    {
        Environment.deleteDhis2Users();
        Environment.createDhis2Users( Environment.ORG_UNIT_ID );
    }

    @Test
    public void testTrackedEntityCreation() throws Exception
    {
        String trackedEntityId = Environment.createDhis2TrackedEntity( Environment.ORG_UNIT_ID, "0012345678" );
        Iterable<TrackedEntity> trackedEntities = Environment.DHIS2_CLIENT.get( "tracker/trackedEntities" )
                .withFields( "*" ).withoutPaging()
                .transfer()
                .returnAs( TrackedEntity.class, "trackedEntities" );
        TrackedEntity trackedEntity = trackedEntities.iterator().next();
        assertEquals( trackedEntityId,trackedEntity.getTrackedEntity().get() );
    }
}
