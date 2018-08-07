package org.janelia.flyem.neuprinter.model;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SkelNodeTest {

    @Test
    public void testEqualsAndHashCode() {

        List<Integer> location1 = new ArrayList<>();
        location1.add(0);
        location1.add(1);
        location1.add(5);
        SkelNode skelNode1 = new SkelNode(new Long(10), location1, 3.0f, 2, new SkelNode(), 1);
        List<Integer> location2 = new ArrayList<>();
        location2.add(0);
        location2.add(1);
        location2.add(5);
        SkelNode skelNode2 = new SkelNode(new Long(13), location2, 34.0f, 1, new SkelNode(), 2);
        List<Integer> location3 = new ArrayList<>();
        location3.add(0);
        location3.add(1);
        location3.add(12);
        SkelNode skelNode3 = new SkelNode(new Long(13), location2, 34.0f, 1, new SkelNode(), 3);

        //reflexive
        Assert.assertTrue(skelNode1.equals(skelNode1));
        //symmetric
        Assert.assertTrue(skelNode1.equals(skelNode2) && skelNode2.equals(skelNode1));
        //transitive
        Assert.assertTrue(skelNode1.equals(skelNode2) && skelNode2.equals(skelNode3) && skelNode3.equals(skelNode1));
        //consistent
        Assert.assertTrue(skelNode2.equals(skelNode1) && skelNode2.equals(skelNode1));
        //not equal to null
        Assert.assertTrue(!location2.equals(null));

        Assert.assertNotSame(skelNode1, skelNode2);
        Assert.assertTrue(skelNode1.hashCode() == skelNode2.hashCode());

    }

    @Test
    public void testAddChild() {

        List<Integer> location1 = new ArrayList<>();
        location1.add(0);
        location1.add(1);
        location1.add(5);
        SkelNode skelNode1 = new SkelNode(new Long(10), location1, 3.0f, 2, new SkelNode(), 1);
        List<Integer> location2 = new ArrayList<>();
        location2.add(0);
        location2.add(1);
        location2.add(5);
        SkelNode skelNode2 = new SkelNode(new Long(13), location2, 34.0f, 1, new SkelNode(), 2);
        List<Integer> location3 = new ArrayList<>();
        location3.add(0);
        location3.add(1);
        location3.add(12);
        SkelNode skelNode3 = new SkelNode(new Long(13), location2, 34.0f, 1, new SkelNode(), 3);

        skelNode1.addChild(skelNode2);
        skelNode1.addChild(skelNode3);
        ArrayList childList = new ArrayList<>();
        childList.add(skelNode2);
        childList.add(skelNode3);

        Assert.assertEquals(childList, skelNode1.getChildren());
        Assert.assertEquals(2, skelNode1.getChildren().size());
        Assert.assertEquals(0, skelNode2.getChildren().size());

    }

}
