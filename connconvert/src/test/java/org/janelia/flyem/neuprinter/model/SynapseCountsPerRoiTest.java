package org.janelia.flyem.neuprinter.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class SynapseCountsPerRoiTest {

    @Test
    public void shouldIncrementPrePostAndTotalAppropriately() {

        SynapseCountsPerRoi synapseCountsPerRoi = new SynapseCountsPerRoi();

        Assert.assertEquals(0, synapseCountsPerRoi.getSynapseCountsPerRoi().size());

        synapseCountsPerRoi.incrementPreForRoi("testRoi");

        Assert.assertEquals(1, synapseCountsPerRoi.getSynapseCountsForRoi("testRoi").getPre());
        Assert.assertEquals(0, synapseCountsPerRoi.getSynapseCountsForRoi("testRoi").getPost());
        Assert.assertEquals(1, synapseCountsPerRoi.getSynapseCountsForRoi("testRoi").getTotal());

        synapseCountsPerRoi.incrementPostForRoi("testRoi");

        Assert.assertEquals(1, synapseCountsPerRoi.getSynapseCountsForRoi("testRoi").getPre());
        Assert.assertEquals(1, synapseCountsPerRoi.getSynapseCountsForRoi("testRoi").getPost());
        Assert.assertEquals(2, synapseCountsPerRoi.getSynapseCountsForRoi("testRoi").getTotal());
    }

    @Test
    public void shouldProduceCorrectJsonString() {

        SynapseCountsPerRoi synapseCountsPerRoi = new SynapseCountsPerRoi();

        synapseCountsPerRoi.incrementPreForRoi("testRoi");
        synapseCountsPerRoi.incrementPostForRoi("testRoi");

        String synapseCountsPerRoiJson = synapseCountsPerRoi.getSynapseCountsPerRoiAsJsonString();

        Gson gson = new Gson();
        Map<String,SynapseCounter> deserializedSynapseCountsPerRoi = gson.fromJson(synapseCountsPerRoiJson, new TypeToken<Map<String,SynapseCounter>>() {}.getType());

        Assert.assertTrue(deserializedSynapseCountsPerRoi.containsKey("testRoi"));
        Assert.assertEquals(2, deserializedSynapseCountsPerRoi.get("testRoi").getTotal());

    }

}
