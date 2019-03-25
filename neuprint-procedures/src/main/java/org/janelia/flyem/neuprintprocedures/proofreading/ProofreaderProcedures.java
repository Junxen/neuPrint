package org.janelia.flyem.neuprintprocedures.proofreading;

import com.google.common.base.Stopwatch;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.janelia.flyem.neuprint.Neo4jImporter;
import org.janelia.flyem.neuprint.model.Neuron;
import org.janelia.flyem.neuprint.model.RoiInfo;
import org.janelia.flyem.neuprint.model.SkelNode;
import org.janelia.flyem.neuprint.model.Skeleton;
import org.janelia.flyem.neuprint.model.Synapse;
import org.janelia.flyem.neuprint.model.SynapseCounter;
import org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools;
import org.janelia.flyem.neuprintloadprocedures.Location;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.spatial.Point;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.BODY_ID;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.CLUSTER_NAME;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.CONFIDENCE;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.CONNECTION_SET;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.CONNECTS_TO;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.CONTAINS;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.DATASET_BODY_ID;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.DATASET_BODY_IDs;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.FROM;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.LINKS_TO;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.LOCATION;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.MUTATION_UUID_ID;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.NAME;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.NEURON;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.POST;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.POST_HP_THRESHOLD;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.PRE;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.PRE_HP_THRESHOLD;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.RADIUS;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.ROI_INFO;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.ROW_NUMBER;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.SEGMENT;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.SIZE;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.SKELETON;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.SKELETON_ID;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.SKEL_NODE;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.SKEL_NODE_ID;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.SOMA_LOCATION;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.SOMA_RADIUS;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.STATUS;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.SUPER_LEVEL_ROIS;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.SYNAPSES_TO;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.SYNAPSE_SET;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.TO;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.TYPE;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.WEIGHT;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.WEIGHT_HP;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.getConnectionSetsForSynapse;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.getMetaNode;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.getSegment;
import static org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.getSynapse;
import static org.janelia.flyem.neuprintloadprocedures.procedures.LoadingProcedures.addPostHPToConnectsTo;
import static org.janelia.flyem.neuprintloadprocedures.procedures.LoadingProcedures.addSynapseToRoiInfoWithHP;
import static org.janelia.flyem.neuprintloadprocedures.procedures.LoadingProcedures.removeSynapseFromRoiInfoWithHP;
import static org.janelia.flyem.neuprintloadprocedures.procedures.LoadingProcedures.setConnectionSetRoiInfoAndGetWeightHP;

public class ProofreaderProcedures {

    public static final Type ROI_INFO_TYPE = new TypeToken<Map<String, SynapseCounter>>() {
    }.getType();
    @Context
    public GraphDatabaseService dbService;
    @Context
    public Log log;

    @Procedure(value = "proofreader.updateProperties", mode = Mode.WRITE)
    @Description("proofreader.updateProperties : Update properties on a Neuron/Segment node.")
    public void updateProperties(@Name("neuronJsonObject") String neuronJsonObject, @Name("datasetLabel") String datasetLabel) {

        log.info("proofreader.updateProperties: entry");

        try {

            if (neuronJsonObject == null || datasetLabel == null) {
                log.error("proofreader.updateProperties: Missing input arguments.");
                throw new RuntimeException("proofreader.updateProperties: Missing input arguments.");
            }

            Gson gson = new Gson();

            Neuron neuron = gson.fromJson(neuronJsonObject, Neuron.class);

            Node neuronNode = getSegment(dbService, neuron.getId(), datasetLabel);

            if (neuronNode == null) {
                log.warn("Neuron with id " + neuron.getId() + " not found in database. Aborting update.");
            } else {

                boolean isNeuron = false;

                if (neuron.getStatus() != null) {
                    neuronNode.setProperty(STATUS, neuron.getStatus());
                    // adding a status makes it a Neuron
                    isNeuron = true;
                    log.info("Updated status for neuron " + neuron.getId() + ".");
                }

                if (neuron.getName() != null) {
                    neuronNode.setProperty(NAME, neuron.getName());
                    // adding a name makes it a Neuron
                    isNeuron = true;
                    log.info("Updated name for neuron " + neuron.getId() + ".");
                }

                if (neuron.getSize() != null) {
                    neuronNode.setProperty(SIZE, neuron.getSize());
                    log.info("Updated size for neuron " + neuron.getId() + ".");
                }

                if (neuron.getSoma() != null) {
                    List<Integer> somaLocationList = neuron.getSoma().getLocation();
                    Point somaLocationPoint = new Location((long) somaLocationList.get(0), (long) somaLocationList.get(1), (long) somaLocationList.get(2));
                    neuronNode.setProperty(SOMA_LOCATION, somaLocationPoint);
                    neuronNode.setProperty(SOMA_RADIUS, neuron.getSoma().getRadius());
                    log.info("Updated soma for neuron " + neuron.getId() + ".");

                    //adding a soma makes it a Neuron
                    isNeuron = true;
                }

                if (neuron.getNeuronType() != null) {
                    neuronNode.setProperty(TYPE, neuron.getNeuronType());
                    log.info("Updated type for neuron " + neuron.getId() + ".");
                }

                if (isNeuron) {
                    convertSegmentToNeuron(neuronNode, datasetLabel, neuron.getId());
                }
            }

        } catch (Exception e) {
            log.error("Error running proofreader.updateProperties: " + e);
            throw new RuntimeException("Error running proofreader.updateProperties: " + e);
        }

        log.info("proofreader.updateProperties: exit");

    }

    private void convertSegmentToNeuron(final Node segment, final String datasetLabel, final Long bodyId) {

        segment.addLabel(Label.label(NEURON));
        segment.addLabel(Label.label(datasetLabel + "-" + NEURON));

        //generate cluster name
        Map<String, SynapseCounter> roiInfoObject = new HashMap<>();
        long totalPre = 0;
        long totalPost = 0;
        boolean setClusterName = true;
        try {
            roiInfoObject = getRoiInfoAsMap((String) segment.getProperty(ROI_INFO));
        } catch (Exception e) {
            log.warn("Error retrieving roiInfo from body id " + bodyId + " in " + datasetLabel + ". No cluster name added.");
            setClusterName = false;
//            throw new RuntimeException("Error retrieving roiInfo from body id " + bodyId + " in " + datasetLabel + ":" + e);
        }

        try {
            totalPre = (long) segment.getProperty(PRE);
        } catch (Exception e) {
            log.warn("Error retrieving pre from body id " + bodyId + " in " + datasetLabel + ". No cluster name added.");
            setClusterName = false;
//            throw new RuntimeException("Error retrieving pre from body id " + bodyId + " in " + datasetLabel + ":" + e);
        }

        try {
            totalPost = (long) segment.getProperty(POST);
        } catch (Exception e) {
            log.warn("Error retrieving post from body id " + bodyId + " in " + datasetLabel + ". No cluster name added.");
            setClusterName = false;
//            throw new RuntimeException("Error retrieving post from body id " + bodyId + " in " + datasetLabel + ":" + e);
        }

        if (setClusterName) {
            Node metaNode = GraphTraversalTools.getMetaNode(dbService, datasetLabel);
            if (metaNode != null) {
                String[] metaNodeSuperLevelRois;
                try {
                    metaNodeSuperLevelRois = (String[]) metaNode.getProperty(SUPER_LEVEL_ROIS);
                } catch (Exception e) {
                    log.error("Error retrieving " + SUPER_LEVEL_ROIS + " from Meta node for " + datasetLabel + ":" + e);
                    throw new RuntimeException("Error retrieving " + SUPER_LEVEL_ROIS + " from Meta node for " + datasetLabel + ":" + e);
                }
                final Set<String> roiSet = new HashSet<>(Arrays.asList(metaNodeSuperLevelRois));
                segment.setProperty("clusterName", Neo4jImporter.generateClusterName(roiInfoObject, totalPre, totalPost, 0.10, roiSet));
            } else {
                log.error("Meta node not found for dataset " + datasetLabel);
                throw new RuntimeException("Meta node not found for dataset " + datasetLabel);
            }
        }

    }

    public static Map<String, SynapseCounter> getRoiInfoAsMap(String roiInfo) {
        Gson gson = new Gson();
        return gson.fromJson(roiInfo, ROI_INFO_TYPE);
    }

    @Procedure(value = "proofreader.deleteSoma", mode = Mode.WRITE)
    @Description("proofreader.deleteSoma(bodyId, datasetLabel): Delete soma (radius and location) from Neuron node.")
    public void deleteSoma(@Name("bodyId") Long bodyId, @Name("datasetLabel") String datasetLabel) {

        log.info("proofreader.deleteSoma: entry");

        try {

            if (bodyId == null || datasetLabel == null) {
                log.error("proofreader.deleteSoma: Missing input arguments.");
                throw new RuntimeException("proofreader.deleteSoma: Missing input arguments.");
            }

            // get the neuron node

            Node neuronNode = getSegment(dbService, bodyId, datasetLabel);

            if (neuronNode == null) {
                log.warn("Neuron with id " + bodyId + " not found in database. Aborting deletion of soma.");
            } else {

                // delete soma radius
                neuronNode.removeProperty(SOMA_RADIUS);

                // delete soma location
                neuronNode.removeProperty(SOMA_LOCATION);

                // check if it should still be labeled neuron and remove designation if necessary
                if (!checkIfStillNeuron(neuronNode)) {
                    System.out.println("Removing neuron designation");
                    removeNeuronDesignationFromNode(neuronNode, datasetLabel);
                }

                log.info("Successfully deleted soma information from " + bodyId);
            }

        } catch (Exception e) {
            log.error("Error running proofreader.deleteSoma: " + e);
            throw new RuntimeException("Error running proofreader.deleteSoma: " + e);
        }

        log.info("proofreader.deleteSoma: exit");

    }

    private boolean checkIfStillNeuron(Node neuronNode) {
        long preCount = 0;
        long postCount = 0;
        if (neuronNode.hasProperty(PRE)) {
            preCount = (long) neuronNode.getProperty(PRE);
        }
        if (neuronNode.hasProperty(POST)) {
            postCount = (long) neuronNode.getProperty(POST);
        }

        // returns true if meets the definition for a neuron
        return (preCount >= 2 || postCount >= 10 || neuronNode.hasProperty(NAME) || neuronNode.hasProperty(SOMA_RADIUS) || neuronNode.hasProperty(STATUS));
    }

    private void removeNeuronDesignationFromNode(Node neuronNode, String datasetLabel) {
        // remove neuron labels
        neuronNode.removeLabel(Label.label(NEURON));
        neuronNode.removeLabel(Label.label(datasetLabel + "-" + NEURON));
        // remove cluster name
        neuronNode.removeProperty(CLUSTER_NAME);
    }

    @Procedure(value = "proofreader.deleteName", mode = Mode.WRITE)
    @Description("proofreader.deleteName(bodyId, datasetLabel): Delete name from Neuron node.")
    public void deleteName(@Name("bodyId") Long bodyId, @Name("datasetLabel") String datasetLabel) {

        log.info("proofreader.deleteName: entry");

        try {

            if (bodyId == null || datasetLabel == null) {
                log.error("proofreader.deleteName: Missing input arguments.");
                throw new RuntimeException("proofreader.deleteName: Missing input arguments.");
            }

            // get the neuron node
            Node neuronNode = getSegment(dbService, bodyId, datasetLabel);

            if (neuronNode == null) {
                log.warn("Neuron with id " + bodyId + " not found in database. Aborting deletion of name.");
            } else {

                // delete name
                neuronNode.removeProperty(NAME);

                // check if it should still be labeled neuron and remove designation if necessary
                if (!checkIfStillNeuron(neuronNode)) {
                    removeNeuronDesignationFromNode(neuronNode, datasetLabel);
                }

                log.info("Successfully deleted name information from " + bodyId);
            }

        } catch (Exception e) {
            log.error("Error running proofreader.deleteName: " + e);
            throw new RuntimeException("Error running proofreader.deleteName: " + e);
        }

        log.info("proofreader.deleteName: exit");
    }

    @Procedure(value = "proofreader.deleteStatus", mode = Mode.WRITE)
    @Description("proofreader.deleteStatus(bodyId, datasetLabel): Delete name from Neuron node.")
    public void deleteStatus(@Name("bodyId") Long bodyId, @Name("datasetLabel") String datasetLabel) {

        log.info("proofreader.deleteStatus: entry");

        try {

            if (bodyId == null || datasetLabel == null) {
                log.error("proofreader.deleteStatus: Missing input arguments.");
                throw new RuntimeException("proofreader.deleteStatus: Missing input arguments.");
            }

            // get the neuron node
            Node neuronNode = getSegment(dbService, bodyId, datasetLabel);

            if (neuronNode == null) {
                log.warn("Neuron with id " + bodyId + " not found in database. Aborting deletion of status.");
            } else {

                // delete status
                neuronNode.removeProperty(STATUS);

                // check if it should still be labeled neuron and remove designation if necessary
                if (!checkIfStillNeuron(neuronNode)) {
                    removeNeuronDesignationFromNode(neuronNode, datasetLabel);
                }

                log.info("Successfully deleted status information from " + bodyId);
            }

        } catch (Exception e) {
            log.error("Error running proofreader.deleteStatus: " + e);
            throw new RuntimeException("Error running proofreader.deleteStatus: " + e);
        }

        log.info("proofreader.deleteStatus: exit");
    }

    @Procedure(value = "proofreader.deleteNeuron", mode = Mode.WRITE)
    @Description("proofreader.deleteNeuron(bodyId, datasetLabel) : Delete a neuron from the database.")
    public void deleteNeuron(@Name("bodyId") Long bodyId, @Name("datasetLabel") String datasetLabel) {

        log.info("proofreader.deleteNeuron: entry");

        try {

            if (bodyId == null || datasetLabel == null) {
                log.error("proofreader.deleteNeuron: Missing input arguments.");
                throw new RuntimeException("proofreader.deleteNeuron: Missing input arguments.");
            }

            deleteSegment(bodyId, datasetLabel);

        } catch (Exception e) {
            log.error("Error running proofreader.deleteNeuron: " + e.getMessage());
            throw new RuntimeException("Error running proofreader.deleteNeuron: " + e.getMessage());
        }

        log.info("proofreader.deleteNeuron: exit");

    }

    private void deleteSegment(long bodyId, String datasetLabel) {

        final Node neuron = GraphTraversalTools.getSegment(dbService, bodyId, datasetLabel);

        if (neuron == null) {
            log.info("Segment with body ID " + bodyId + " not found in database. Aborting deletion...");
        } else {
            acquireWriteLockForSegmentSubgraph(neuron);

            if (neuron.hasRelationship(RelationshipType.withName(CONTAINS), Direction.OUTGOING)) {
                for (Relationship neuronContainsRel : neuron.getRelationships(RelationshipType.withName(CONTAINS), Direction.OUTGOING)) {
                    final Node containedNode = neuronContainsRel.getEndNode();
                    if (containedNode.hasLabel(Label.label(SYNAPSE_SET))) {
                        // delete the connection to the current node
                        neuronContainsRel.delete();
                        // delete relationships to synapses
                        containedNode.getRelationships().forEach(Relationship::delete);
                        //delete synapse set
                        containedNode.delete();
                    } else if (containedNode.hasLabel(Label.label(SKELETON))) {
                        // delete neuron relationship to skeleton
                        neuronContainsRel.delete();
                        // delete skeleton and skelnodes
                        deleteSkeleton(containedNode);
                    }
                }
            }

            //delete connection sets
            deleteConnectionSetsAndRelationships(neuron, FROM);
            deleteConnectionSetsAndRelationships(neuron, TO);

            // delete ConnectsTo relationships
            if (neuron.hasRelationship(RelationshipType.withName(CONNECTS_TO))) {
                neuron.getRelationships(RelationshipType.withName(CONNECTS_TO)).forEach(Relationship::delete);
            }

            // delete Neuron/Segment node
            neuron.delete();
            log.info("Deleted segment with body Id: " + bodyId);

        }

    }

    private void acquireWriteLockForSegmentSubgraph(Node segment) {
        // neuron
        acquireWriteLockForNode(segment);
        // connects to relationships and 1-degree connections
        for (Relationship connectsToRelationship : segment.getRelationships(RelationshipType.withName(CONNECTS_TO))) {
            acquireWriteLockForRelationship(connectsToRelationship);
            acquireWriteLockForNode(connectsToRelationship.getOtherNode(segment));
        }
        // skeleton and synapse set
        for (Relationship containsRelationship : segment.getRelationships(RelationshipType.withName(CONTAINS))) {
            acquireWriteLockForRelationship(containsRelationship);
            Node skeletonOrSynapseSetNode = containsRelationship.getEndNode();
            acquireWriteLockForNode(skeletonOrSynapseSetNode);
            // skel nodes and synapses
            for (Relationship skelNodeOrSynapseRelationship : skeletonOrSynapseSetNode.getRelationships(RelationshipType.withName(CONTAINS), Direction.OUTGOING)) {
                acquireWriteLockForRelationship(skelNodeOrSynapseRelationship);
                Node skelNodeOrSynapseNode = skelNodeOrSynapseRelationship.getEndNode();
                acquireWriteLockForNode(skelNodeOrSynapseNode);
                // first degree relationships to synapses
                for (Relationship synapsesToRelationship : skelNodeOrSynapseNode.getRelationships(RelationshipType.withName(SYNAPSES_TO))) {
                    acquireWriteLockForRelationship(synapsesToRelationship);
                    acquireWriteLockForNode(synapsesToRelationship.getOtherNode(skelNodeOrSynapseNode));
                }
                // links to relationships for skel nodes
                for (Relationship linksToRelationship : skelNodeOrSynapseNode.getRelationships(RelationshipType.withName(LINKS_TO), Direction.OUTGOING)) {
                    acquireWriteLockForRelationship(linksToRelationship);
                }
            }
        }
        // connection sets
        for (Relationship toRelationship : segment.getRelationships(RelationshipType.withName(TO))) {
            acquireWriteLockForRelationship(toRelationship);
            Node connectionSetNode = toRelationship.getStartNode();
            acquireWriteLockForNode(connectionSetNode);
            Relationship fromRelationship = connectionSetNode.getSingleRelationship(RelationshipType.withName(FROM), Direction.OUTGOING);
            acquireWriteLockForRelationship(fromRelationship);
        }
        for (Relationship fromRelationship : segment.getRelationships(RelationshipType.withName(FROM))) {
            acquireWriteLockForRelationship(fromRelationship);
            Node connectionSetNode = fromRelationship.getStartNode();
            Relationship toRelationship = connectionSetNode.getSingleRelationship(RelationshipType.withName(TO), Direction.OUTGOING);
            acquireWriteLockForRelationship(toRelationship);
        }
    }

    private void deleteSkeleton(final Node skeletonNode) {

        Set<Node> skelNodesToDelete = new HashSet<>();
        for (Relationship skeletonRelationship : skeletonNode.getRelationships(RelationshipType.withName(CONTAINS), Direction.OUTGOING)) {
            Node skelNode = skeletonRelationship.getEndNode();
            //delete LinksTo relationships and
            skelNode.getRelationships(RelationshipType.withName(LINKS_TO)).forEach(Relationship::delete);
            //delete SkelNode Contains relationship to Skeleton
            skeletonRelationship.delete();
            skelNodesToDelete.add(skelNode);
        }

        //delete SkelNodes at end to avoid missing node errors
        skelNodesToDelete.forEach(Node::delete);

        //delete Skeleton
        skeletonNode.delete();
        log.info("Successfully deleted skeleton.");
    }

    private void deleteConnectionSetsAndRelationships(final Node neuron, String type) {
        if (neuron.hasRelationship(RelationshipType.withName(type), Direction.INCOMING)) {
            for (Relationship fromRelationship : neuron.getRelationships(RelationshipType.withName(type))) {
                final Node connectionSet = fromRelationship.getStartNode();
                // delete relationships of connection set
                connectionSet.getRelationships().forEach(Relationship::delete);
                // delete connection set
                connectionSet.delete();
            }
        }
    }

    private void acquireWriteLockForNode(Node node) {
        try (Transaction tx = dbService.beginTx()) {
            tx.acquireWriteLock(node);
            tx.success();
        }
    }

    private void acquireWriteLockForRelationship(Relationship relationship) {
        try (Transaction tx = dbService.beginTx()) {
            tx.acquireWriteLock(relationship);
            tx.success();
        }
    }

    @Procedure(value = "proofreader.addNeuron", mode = Mode.WRITE)
    @Description("proofreader.addNeuron(neuronAdditionJsonObject, datasetLabel): add a neuron with properties, synapses, and connections specified by an input JSON.")
    public void addNeuron(@Name("neuronAdditionJson") String neuronAdditionJson, @Name("datasetLabel") String datasetLabel) {

        log.info("proofreader.addNeuron: entry");

        try {

            if (neuronAdditionJson == null || datasetLabel == null) {
                log.error("proofreader.addNeuron: Missing input arguments.");
                throw new RuntimeException("proofreader.addNeuron: Missing input arguments.");
            }

            Gson gson = new Gson();
            NeuronAddition neuronAddition = gson.fromJson(neuronAdditionJson, NeuronAddition.class);

            if (neuronAddition.getMutationUuid() == null || neuronAddition.getBodyId() == null) {
                log.error("proofreader.addNeuron: body id and uuid are required fields in the neuron addition json.");
                throw new RuntimeException("proofreader.addNeuron: body id and uuid are required fields in the neuron addition json.");
            }

            if (neuronAddition.getMutationId() == null) {
                neuronAddition.setToInitialMutationId();
            }

            // check that this mutation hasn't been done before (in order to be unique, needs to include uuid+mutationid+bodyId)
            String mutationKey = neuronAddition.getMutationUuid() + ":" + neuronAddition.getMutationId() + ":" + neuronAddition.getBodyId();
            Node existingMutatedNode = dbService.findNode(Label.label(datasetLabel + "-" + SEGMENT), MUTATION_UUID_ID, mutationKey);
            if (existingMutatedNode != null) {
                log.error("Mutation already found in the database: " + neuronAddition.toString());
                throw new RuntimeException("Mutation already found in the database: " + neuronAddition.toString());
            }

            log.info("Beginning addition: " + neuronAddition);
            // create a new node and synapse set for that node
            final long newNeuronBodyId = neuronAddition.getBodyId();
            final Node newNeuron = dbService.createNode(Label.label(SEGMENT),
                    Label.label(datasetLabel),
                    Label.label(datasetLabel + "-" + SEGMENT));

            try {
                newNeuron.setProperty(BODY_ID, newNeuronBodyId);
            } catch (org.neo4j.graphdb.ConstraintViolationException cve) {
                log.error("Body id " + newNeuronBodyId + " already exists in database. Aborting addition for mutation with id : " + mutationKey);
                throw new RuntimeException("Body id " + newNeuronBodyId + " already exists in database. Aborting addition for mutation with id : " + mutationKey);
            }

            final Node newSynapseSet = createSynapseSetForSegment(newNeuron, datasetLabel);

            // add appropriate synapses via synapse sets; add each synapse to the new body's synapseset
            // completely add everything here so that there's nothing left on the synapse store
            // add the new body id to the synapse sources

            Set<Synapse> currentSynapses = neuronAddition.getCurrentSynapses();
            long preCount = 0L;
            long postCount = 0L;

            if (currentSynapses != null) {
                Set<Synapse> notFoundSynapses = new HashSet<>(currentSynapses);

                // from synapses, derive connectsto, connection sets, rois/roiInfo, pre/post counts
                final ConnectsToRelationshipMap connectsToRelationshipMap = new ConnectsToRelationshipMap();
                final RoiInfo roiInfo = new RoiInfo();

                for (Synapse synapse : currentSynapses) {

                    // get the synapse by location
                    List<Integer> synapseLocation = synapse.getLocation();
                    Point synapseLocationPoint = new Location((long) synapseLocation.get(0), (long) synapseLocation.get(1), (long) synapseLocation.get(2));
                    Node synapseNode = getSynapse(dbService, synapseLocationPoint, datasetLabel);

                    if (synapseNode == null) {
                        log.error("Synapse not found in database: " + synapse);
                        throw new RuntimeException("Synapse not found in database: " + synapse);
                    }

                    if (synapseNode.hasRelationship(RelationshipType.withName(CONTAINS))) {
                        Node bodyWithSynapse = getSegmentThatContainsSynapse(synapseNode);
                        Long bodyWithSynapseId = (Long) bodyWithSynapse.getProperty(BODY_ID);
                        log.error("Synapse is already assigned to another body. body id: " + bodyWithSynapseId + ", synapse: " + synapse);
                        throw new RuntimeException("Synapse is already assigned to another body. body id: " + bodyWithSynapseId + ", synapse: " + synapse);
                    }

                    // add synapse to the new synapse set
                    newSynapseSet.createRelationshipTo(synapseNode, RelationshipType.withName(CONTAINS));
                    // remove this synapse from the not found set
                    notFoundSynapses.remove(synapse);

                    // get the synapse type
                    final String synapseType = (String) synapseNode.getProperty(TYPE);
                    // get synapse rois for adding to the body and roiInfo
                    final Set<String> synapseRois = getSynapseNodeRoiSet(synapseNode);

                    if (synapseType.equals(PRE)) {
                        for (String roi : synapseRois) {
                            roiInfo.incrementPreForRoi(roi);
                        }
                        preCount++;
                    } else if (synapseType.equals(POST)) {
                        for (String roi : synapseRois) {
                            roiInfo.incrementPostForRoi(roi);
                        }
                        postCount++;
                    }

                    if (synapseNode.hasRelationship(RelationshipType.withName(SYNAPSES_TO))) {
                        for (Relationship synapticRelationship : synapseNode.getRelationships(RelationshipType.withName(SYNAPSES_TO))) {
                            Node synapticPartner = synapticRelationship.getOtherNode(synapseNode);

                            Node connectedSegment = getSegmentThatContainsSynapse(synapticPartner);

                            if (connectedSegment != null) {
                                if (synapseType.equals(PRE)) {
                                    connectsToRelationshipMap.insertSynapsesIntoConnectsToRelationship(newNeuron, connectedSegment, synapseNode, synapticPartner);
                                } else if (synapseType.equals(POST)) {
                                    connectsToRelationshipMap.insertSynapsesIntoConnectsToRelationship(connectedSegment, newNeuron, synapticPartner, synapseNode);
                                }
                            }
                        }
                    }

                }

                if (!notFoundSynapses.isEmpty()) {
                    log.error("Some synapses were not found for neuron addition. Mutation UUID: " + neuronAddition.getMutationUuid() + " Mutation ID: " + neuronAddition.getMutationId() + " Synapse(s): " + notFoundSynapses);
                    throw new RuntimeException("Some synapses were not found for neuron addition. Mutation UUID: " + neuronAddition.getMutationUuid() + " Mutation ID: " + neuronAddition.getMutationId() + " Synapse(s): " + notFoundSynapses);
                }

                log.info("Found and added all synapses to synapse set for body id " + newNeuronBodyId);
                log.info("Completed making map of ConnectsTo relationships.");

                // add synapse and synaptic partners to connection set; set connectsto relationships
                createConnectionSetsAndConnectsToRelationships(connectsToRelationshipMap, datasetLabel);
                log.info("Completed creating ConnectionSets and ConnectsTo relationships.");

                // add roi boolean properties and roi info
                addRoiPropertiesToSegmentGivenSynapseCountsPerRoi(newNeuron, roiInfo);
                newNeuron.setProperty(ROI_INFO, roiInfo.getAsJsonString());
                log.info("Completed updating roi information.");

            }
            // set pre and post on body; other properties
            newNeuron.setProperty(PRE, preCount);
            newNeuron.setProperty(POST, postCount);
            newNeuron.setProperty(SIZE, neuronAddition.getSize());
            newNeuron.setProperty(MUTATION_UUID_ID, mutationKey);

            // check for optional properties; add to neuron if present; decide if there should be a neuron label (has name, has soma, has pre+post>10)
            boolean isNeuron = false;
            if (neuronAddition.getStatus() != null) {
                newNeuron.setProperty(STATUS, neuronAddition.getStatus());
            }

            if (neuronAddition.getName() != null) {
                newNeuron.setProperty(NAME, neuronAddition.getName());
                isNeuron = true;
            }

            if (neuronAddition.getSoma() != null) {
                newNeuron.setProperty(SOMA_RADIUS, neuronAddition.getSoma().getRadius());
                List<Integer> somaLocation = neuronAddition.getSoma().getLocation();
                Point somaLocationPoint = new Location((long) somaLocation.get(0), (long) somaLocation.get(1), (long) somaLocation.get(2));
                newNeuron.setProperty(SOMA_LOCATION, somaLocationPoint);
                isNeuron = true;
            }

            if (preCount >= 2 || postCount >= 10) {
                isNeuron = true;
            }

            if (isNeuron) {
                convertSegmentToNeuron(newNeuron, datasetLabel, newNeuronBodyId);
            }

            // update meta node
            Node metaNode = GraphTraversalTools.getMetaNode(dbService, datasetLabel);
            metaNode.setProperty("latestMutationId", neuronAddition.getMutationId());
            metaNode.setProperty("uuid", neuronAddition.getMutationUuid());

//            add skeleton?

            log.info("Completed neuron addition with uuid " + neuronAddition.getMutationUuid() + ", mutation id " + neuronAddition.getMutationId() + ", body id " + neuronAddition.getBodyId() + ".");

        } catch (Exception e) {
            log.error("Error running proofreader.addNeuron: " + e);
            throw new RuntimeException("Error running proofreader.addNeuron: " + e);
        }

        log.info("proofreader.addNeuron: exit");

    }

//    private void mergeSynapseSets(Node synapseSet1, Node synapseSet2) {
//
//        //add both synapse sets to the new node, collect them for adding to apoc merge procedure
//        Map<String, Object> parametersMap = new HashMap<>();
//        if (node1SynapseSetNode != null) {
//            newNode.createRelationshipTo(node1SynapseSetNode, RelationshipType.withName(CONTAINS));
//            parametersMap.put("ssnode1", node1SynapseSetNode);
//        }
//        if (node2SynapseSetNode != null) {
//            newNode.createRelationshipTo(node2SynapseSetNode, RelationshipType.withName(CONTAINS));
//            parametersMap.put("ssnode2", node2SynapseSetNode);
//        }
//
//        // merge the two synapse nodes using apoc if there are two synapseset nodes. inherits the datasetBodyId of the first node
//        if (parametersMap.containsKey("ssnode1") && parametersMap.containsKey("ssnode2")) {
//            dbService.execute("CALL apoc.refactor.mergeNodes([$ssnode1, $ssnode2], {properties:{datasetBodyId:\"discard\"}}) YIELD node RETURN node", parametersMap).next().get("node");
//            //delete the extra relationship between new node and new synapse set node
//            newNode.getRelationships(RelationshipType.withName(CONTAINS)).iterator().next().delete();
//        }
//    }

    private Node createSynapseSetForSegment(final Node segment, final String datasetLabel) {
        final Node newSynapseSet = dbService.createNode(Label.label(SYNAPSE_SET), Label.label(datasetLabel), Label.label(datasetLabel + "-" + SYNAPSE_SET));
        final long segmentBodyId = (long) segment.getProperty(BODY_ID);
        newSynapseSet.setProperty(DATASET_BODY_ID, datasetLabel + ":" + segmentBodyId);
        segment.createRelationshipTo(newSynapseSet, RelationshipType.withName(CONTAINS));
        return newSynapseSet;
    }

    private Node getSegmentThatContainsSynapse(Node synapseNode) {
        Node connectedSegment = GraphTraversalTools.getSegmentThatContainsSynapse(synapseNode);
        if (connectedSegment == null) {
            log.info("Synapse does not belong to a body: " + synapseNode.getAllProperties());
        }
        return connectedSegment;
    }

    private Set<String> getSynapseNodeRoiSet(Node synapseNode) {
        return GraphTraversalTools.getSynapseRois(synapseNode);
    }

    private void createConnectionSetsAndConnectsToRelationships(ConnectsToRelationshipMap connectsToRelationshipMap, String datasetLabel) {

        for (String connectionKey : connectsToRelationshipMap.getSetOfConnectionKeys()) {
            final ConnectsToRelationship connectsToRelationship = connectsToRelationshipMap.getConnectsToRelationshipByKey(connectionKey);
            final Node startNode = connectsToRelationship.getStartNode();
            final Node endNode = connectsToRelationship.getEndNode();
            final long weight = connectsToRelationship.getWeight();
            // create a ConnectsTo relationship
            Relationship connectsToRel = addConnectsToRelationship(startNode, endNode, weight);

            // create a ConnectionSet node
            final Node connectionSet = dbService.createNode(Label.label(CONNECTION_SET), Label.label(datasetLabel), Label.label(datasetLabel + "-" + CONNECTION_SET));
            final long startNodeBodyId = (long) startNode.getProperty(BODY_ID);
            final long endNodeBodyId = (long) endNode.getProperty(BODY_ID);
            connectionSet.setProperty(DATASET_BODY_IDs, datasetLabel + ":" + startNodeBodyId + ":" + endNodeBodyId);

            // connect it to start and end bodies
            connectionSet.createRelationshipTo(startNode, RelationshipType.withName(FROM));
            connectionSet.createRelationshipTo(endNode, RelationshipType.withName(TO));

            final Set<Node> synapsesForConnectionSet = connectsToRelationship.getSynapsesInConnectionSet();

            // add synapses to ConnectionSet
            for (final Node synapse : synapsesForConnectionSet) {
                // connection set Contains synapse
                connectionSet.createRelationshipTo(synapse, RelationshipType.withName(CONTAINS));
            }

            // add roi info to connection sets and weight hp to connections
            // get pre and post thresholds from meta node (if not present use 0.0)
            Map<String, Double> thresholdMap = getPreAndPostHPThresholdFromMetaNode(datasetLabel);

            int postHPCount = setConnectionSetRoiInfoAndGetWeightHP(synapsesForConnectionSet, connectionSet, thresholdMap.get(PRE_HP_THRESHOLD), thresholdMap.get(POST_HP_THRESHOLD));
            connectsToRel.setProperty(WEIGHT_HP, postHPCount);

        }

    }

    private void addRoiPropertiesToSegmentGivenSynapseCountsPerRoi(Node segment, RoiInfo roiInfo) {
        for (String roi : roiInfo.getSetOfRois()) {
            segment.setProperty(roi, true);
        }
    }

    private Relationship addConnectsToRelationship(Node startNode, Node endNode, long weight) {
        // create a ConnectsTo relationship
        Relationship relationship = startNode.createRelationshipTo(endNode, RelationshipType.withName(CONNECTS_TO));
        relationship.setProperty(WEIGHT, weight);
        return relationship;
    }

    private Map<String, Double> getPreAndPostHPThresholdFromMetaNode(String datasetLabel) {
        Node metaNode = getMetaNode(dbService, datasetLabel);
        Map<String, Double> thresholdMap = new HashMap<>();
        if (metaNode.hasProperty(PRE_HP_THRESHOLD)) {
            thresholdMap.put(PRE_HP_THRESHOLD, (Double) metaNode.getProperty(PRE_HP_THRESHOLD));
        } else {
            thresholdMap.put(PRE_HP_THRESHOLD, 0.0);
        }
        if (metaNode.hasProperty(POST_HP_THRESHOLD)) {
            thresholdMap.put(POST_HP_THRESHOLD, (Double) metaNode.getProperty(POST_HP_THRESHOLD));
        } else {
            thresholdMap.put(POST_HP_THRESHOLD, 0.0);
        }

        return thresholdMap;
    }

    @Procedure(value = "proofreader.addSkeleton", mode = Mode.WRITE)
    @Description("proofreader.addSkeleton(fileUrl,datasetLabel) : load skeleton from provided url and connect to its associated neuron/segment (note: file URL must contain body id of neuron/segment) ")
    public void addSkeleton(@Name("fileUrl") String fileUrlString, @Name("datasetLabel") String datasetLabel) {

        log.info("proofreader.addSkeleton: entry");

        try {

            if (fileUrlString == null || datasetLabel == null) {
                log.error("proofreader.addSkeleton: Missing input arguments.");
                throw new RuntimeException("proofreader.addSkeleton: Missing input arguments.");
            }

            String bodyIdPattern = ".*/(.*?)[._]swc";
            Matcher bodyIdMatcher = Pattern.compile(bodyIdPattern).matcher(fileUrlString);
            Long bodyId;
            boolean bodyIdMatches = bodyIdMatcher.matches();
            if (bodyIdMatches) {
                bodyId = Long.parseLong(bodyIdMatcher.group(1));
            } else {
                log.error("proofreader.addSkeleton: Cannot parse body id from swc file path.");
                throw new RuntimeException("proofreader.addSkeleton: Cannot parse body id from swc file path.");
            }

            String uuidPattern = ".*/api/node/(.*?)/segmentation.*";
            Matcher uuidMatcher = Pattern.compile(uuidPattern).matcher(fileUrlString);
            Optional<String> uuid = Optional.empty();
            boolean uuidMatches = uuidMatcher.matches();
            if (uuidMatches) {
                uuid = Optional.ofNullable(uuidMatcher.group(1));
            }

            Skeleton skeleton = new Skeleton();
            URL fileUrl;

            Stopwatch timer = Stopwatch.createStarted();
            try {
                fileUrl = new URL(fileUrlString);
            } catch (MalformedURLException e) {
                log.error(String.format("proofreader.addSkeleton: Malformed URL: %s", e.getMessage()));
                throw new RuntimeException(String.format("Malformed URL: %s", e.getMessage()));
            }
            log.info("Time to create url:" + timer.stop());
            timer.reset();

            timer.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(fileUrl.openStream()))) {
                skeleton.fromSwc(reader, bodyId, uuid.orElse("none"));
            } catch (IOException e) {
                log.error(String.format("proofreader.addSkeleton: IOException: %s", e.getMessage()));
                throw new RuntimeException(String.format("IOException: %s", e.getMessage()));
            }
            log.info("Time to read in swc file: " + timer.stop());
            timer.reset();

            timer.start();
            Node segment = GraphTraversalTools.getSegment(dbService, bodyId, datasetLabel);
            log.info("Time to get segment:" + timer.stop());
            timer.reset();

            if (segment != null) {
                // grab write locks upfront
                acquireWriteLockForSegmentSubgraph(segment);

                timer.start();
                //check if skeleton already exists
                Node existingSkeleton = GraphTraversalTools.getSkeleton(dbService, bodyId, datasetLabel);
                if (existingSkeleton != null) {
                    log.warn(String.format("proofreader.addSkeleton: Skeleton for body ID %d already exists in dataset %s. Aborting addSkeleton.", bodyId, datasetLabel));
                } else {

                    addSkeletonNodes(datasetLabel, skeleton, segment);

                    log.info("Successfully added Skeleton to body Id " + bodyId + ".");
                }
                log.info("Time to add skeleton:" + timer.stop());
                timer.reset();

            } else {
                log.warn("Body Id " + bodyId + " does not exist in the dataset.");
            }

        } catch (Exception e) {
            log.error("Error running proofreader.addSkeleton: " + e);
            throw new RuntimeException("Error running proofreader.addSkeleton: " + e);
        }

        log.info("proofreader.addSkeleton: exit");

    }

    private Node addSkeletonNodes(final String dataset, final Skeleton skeleton, final Node segmentNode) {

        // create a skeleton node and connect it to the body
        Node skeletonNode = dbService.createNode(Label.label(SKELETON), Label.label(dataset + "-" + SKELETON), Label.label(dataset));
        skeletonNode.setProperty(SKELETON_ID, dataset + ":" + skeleton.getAssociatedBodyId());
        skeletonNode.setProperty(MUTATION_UUID_ID, skeleton.getMutationUuid().orElse("none") + ":" + skeleton.getMutationId().orElse(0L));
        segmentNode.createRelationshipTo(skeletonNode, RelationshipType.withName(CONTAINS));

        //add root nodes / other nodes to skeleton node
        List<SkelNode> skelNodeList = skeleton.getSkelNodeList();

        for (SkelNode skelNode : skelNodeList) {

            // create skelnode with id
            //try to get the node first, if it doesn't exist create it
            String skelNodeId = skelNode.getSkelNodeId(dataset);
            Node skelNodeNode = GraphTraversalTools.getSkelNode(dbService, skelNodeId, dataset);

            if (skelNodeNode == null) {
                skelNodeNode = createSkelNode(skelNodeId, dataset, skelNode);
            }

            //connect the skelnode to the skeleton
            skeletonNode.createRelationshipTo(skelNodeNode, RelationshipType.withName(CONTAINS));

            // add the children
            for (SkelNode childSkelNode : skelNode.getChildren()) {

                String childNodeId = childSkelNode.getSkelNodeId(dataset);
                Node childSkelNodeNode = dbService.findNode(Label.label(dataset + "-" + SKEL_NODE), SKEL_NODE_ID, childNodeId);
                if (childSkelNodeNode == null) {
                    childSkelNodeNode = createSkelNode(childNodeId, dataset, childSkelNode);
                }

                // add a link to the parent
                skelNodeNode.createRelationshipTo(childSkelNodeNode, RelationshipType.withName(LINKS_TO));

            }
        }

        return skeletonNode;
    }

    private Node createSkelNode(String skelNodeId, String dataset, SkelNode skelNode) {
        Node skelNodeNode = dbService.createNode(Label.label(SKEL_NODE), Label.label(dataset + "-" + SKEL_NODE), Label.label(dataset));
        skelNodeNode.setProperty(SKEL_NODE_ID, skelNodeId);

        //set location
        List<Integer> skelNodeLocation = skelNode.getLocation();
        Point skelNodeLocationPoint = new Location((long) skelNodeLocation.get(0), (long) skelNodeLocation.get(1), (long) skelNodeLocation.get(2));
        skelNodeNode.setProperty(LOCATION, skelNodeLocationPoint);

        //set radius, row number, type
        skelNodeNode.setProperty(RADIUS, skelNode.getRadius());
        skelNodeNode.setProperty(ROW_NUMBER, skelNode.getRowNumber());
        skelNodeNode.setProperty(TYPE, skelNode.getType());

        return skelNodeNode;
    }

//
//    private void setSynapseRoisFromDatabase(Synapse synapse, String datasetLabel) {
//        List<String> roiList = getRoisForSynapse(synapse, datasetLabel);
//        if (roiList.size() == 0) {
//            log.info(String.format("ProofreaderProcedures setSynapseRoisFromDatabase: No roi found on %s: %s", SYNAPSE, synapse));
//            //throw new RuntimeException(String.format("No roi found on %s: %s", SYNAPSE, synapse));
//        }
//        synapse.addRoiList(new ArrayList<>(roiList));
//    }

    @Procedure(value = "proofreader.deleteSkeleton", mode = Mode.WRITE)
    @Description("proofreader.deleteSkeleton(bodyId,datasetLabel) : delete skeleton for provided body id ")
    public void deleteSkeleton(@Name("bodyId") Long bodyId, @Name("datasetLabel") String datasetLabel) {

        log.info("proofreader.deleteSkeleton: entry");

        try {

            if (bodyId == null || datasetLabel == null) {
                log.error("proofreader.deleteSkeleton: Missing input arguments.");
                throw new RuntimeException("proofreader.deleteSkeleton: Missing input arguments.");
            }

            Node neuron = GraphTraversalTools.getSegment(dbService, bodyId, datasetLabel);

            if (neuron != null) {

                acquireWriteLockForSegmentSubgraph(neuron);

                Node skeleton = GraphTraversalTools.getSkeletonNodeForNeuron(neuron);

                if (skeleton != null) {
                    log.info("proofreader.deleteSkeleton: skeleton found for body id " + bodyId + ".");
                    // delete neuron relationship to skeleton
                    skeleton.getSingleRelationship(RelationshipType.withName(CONTAINS), Direction.INCOMING).delete();
                    deleteSkeleton(skeleton);
                } else {
                    log.warn("proofreader.deleteSkeleton: no skeleton found for body id " + bodyId + ". Aborting deletion...");
                }

            } else {
                log.warn("proofreader.deleteSkeleton: body id " + bodyId + " not found. Aborting deletion...");
            }

        } catch (Exception e) {
            log.error("Error running proofreader.deleteSkeleton: " + e);
            throw new RuntimeException("Error running proofreader.deleteSkeleton: " + e);
        }

        log.info("proofreader.deleteSkeleton: exit");

    }

    @Procedure(value = "proofreader.addRoiToSynapse", mode = Mode.WRITE)
    @Description("proofreader.addRoiToSynapse(x,y,z,roiName,dataset) : add an ROI to a synapse. ")
    public void addRoiToSynapse(@Name("x") final Double x, @Name("y") final Double y, @Name("z") final Double z, @Name("roiName") final String roiName, @Name("dataset") final String dataset) {

        log.info("proofreader.addRoiToSynapse: entry");

        try {

            if (x == null || y == null || z == null || roiName == null || dataset == null) {
                log.error("proofreader.addRoiToSynapse: Missing input arguments.");
                throw new RuntimeException("proofreader.addRoiToSynapse: Missing input arguments.");
            }

            Node synapse = getSynapse(dbService, x, y, z, dataset);

            Node neuron = getSegmentThatContainsSynapse(synapse);
            acquireWriteLockForSegmentSubgraph(neuron);
            Node metaNode = getMetaNode(dbService, dataset);
            acquireWriteLockForNode(metaNode);

            if (synapse == null) {
                log.error("proofreader.addRoiToSynapse: No synapse found at location: [" + x + "," + y + "," + z + "]");
                throw new RuntimeException("proofreader.addRoiToSynapse: No synapse found at location: [" + x + "," + y + "," + z + "]");
            }

            boolean roiAlreadyPresent = synapse.hasProperty(roiName);

            if (!roiAlreadyPresent) {

                // add roi to synapse
                synapse.setProperty(roiName, true);

                String synapseType = (String) synapse.getProperty(TYPE);
                if (synapseType == null) {
                    log.warn("proofreader.addRoiToSynapse: No type value found on synapse: " + synapse.getAllProperties());
                }
                Double synapseConfidence = (Double) synapse.getProperty(CONFIDENCE);
                if (synapseConfidence == null) {
                    log.warn("proofreader.addRoiToSynapse: No confidence value found on synapse: " + synapse.getAllProperties());
                }

                // update connection set counts
                // get the connection sets that it's part of
                List<Node> connectionSetList = getConnectionSetsForSynapse(dbService, synapse);
                Map<String, Double> thresholdMap = getPreAndPostHPThresholdFromMetaNode(dataset);
                // change roiInfo for each connection set
                for (Node connectionSetNode : connectionSetList) {

                    String roiInfoString = (String) connectionSetNode.getProperty(ROI_INFO);
                    if (roiInfoString != null) {
                        String roiInfoJsonString = addSynapseToRoiInfoWithHP(roiInfoString, roiName, synapseType, synapseConfidence, thresholdMap.get(PRE_HP_THRESHOLD), thresholdMap.get(POST_HP_THRESHOLD));
                        connectionSetNode.setProperty(ROI_INFO, roiInfoJsonString);
                    } else {
                        log.warn("proofreader.addRoiToSynapse: No roi info found on connection set: " + connectionSetNode.getAllProperties());
                    }
                }

                // update roi info and roi properties on neuron/segment
                if (neuron != null) {
                    // add boolean property
                    neuron.setProperty(roiName, true);

                    // update roi info
                    String roiInfoString = (String) neuron.getProperty(ROI_INFO);
                    if (roiInfoString != null) {

                        String roiInfoJsonString = addSynapseToRoiInfo(roiInfoString, roiName, synapseType);
                        neuron.setProperty(ROI_INFO, roiInfoJsonString);

                    } else {
                        log.warn("proofreader.addRoiToSynapse: No roi info found on neuron: " + neuron.getAllProperties());
                    }
                } else {
                    log.warn("proofreader.addRoiToSynapse: Synapse not connected to neuron: " + synapse.getAllProperties());
                }

                // update meta node
                String metaRoiInfoString = (String) metaNode.getProperty(ROI_INFO);
                if (metaRoiInfoString != null) {
                    String roiInfoJsonString = addSynapseToRoiInfo(metaRoiInfoString, roiName, synapseType);
                    metaNode.setProperty(ROI_INFO, roiInfoJsonString);
                } else {
                    log.warn("proofreader.addRoiToSynapse: No roi info found on meta node for dataset: " + dataset);
                }
            } else {
                log.warn("proofreader.addRoiToSynapse: roi already present on synapse. Ignoring update request: " + synapse.getAllProperties());
            }

        } catch (Exception e) {
            log.error("proofreader.addRoiToSynapse: " + e);
            throw new RuntimeException("proofreader.addRoiToSynapse: " + e);
        }

        log.info("proofreader.addRoiToSynapse: exit");

    }

    private String addSynapseToRoiInfo(String roiInfoString, String roiName, String synapseType) {
        Map<String, SynapseCounter> roiInfoMap = getRoiInfoAsMap(roiInfoString);
        RoiInfo roiInfo = new RoiInfo(roiInfoMap);

        if (synapseType.equals(PRE)) {
            roiInfo.incrementPreForRoi(roiName);
        } else if (synapseType.equals(POST)) {
            roiInfo.incrementPostForRoi(roiName);
        }

        return roiInfo.getAsJsonString();

    }

    @Procedure(value = "proofreader.removeRoiFromSynapse", mode = Mode.WRITE)
    @Description("proofreader.removeRoiFromSynapse(x,y,z,roiName,dataset) : remove an ROI from a synapse. ")
    public void removeRoiFromSynapse(@Name("x") final Double x, @Name("y") final Double y, @Name("z") final Double z, @Name("roiName") final String roiName, @Name("dataset") final String dataset) {

        log.info("proofreader.removeRoiFromSynapse: entry");

        try {

            if (x == null || y == null || z == null || roiName == null || dataset == null) {
                log.error("proofreader.removeRoiFromSynapse: Missing input arguments.");
                throw new RuntimeException("proofreader.removeRoiFromSynapse: Missing input arguments.");
            }

            Node synapse = getSynapse(dbService, x, y, z, dataset);

            Node neuron = getSegmentThatContainsSynapse(synapse);
            acquireWriteLockForSegmentSubgraph(neuron);
            Node metaNode = getMetaNode(dbService, dataset);
            acquireWriteLockForNode(metaNode);

            if (synapse == null) {
                log.error("proofreader.removeRoiFromSynapse: No synapse found at location: [" + x + "," + y + "," + z + "]");
                throw new RuntimeException("proofreader.removeRoiFromSynapse: No synapse found at location: [" + x + "," + y + "," + z + "]");
            }

            boolean roiPresent = synapse.hasProperty(roiName);

            if (roiPresent) {

                // remove roi from synapse
                synapse.removeProperty(roiName);

                String synapseType = (String) synapse.getProperty(TYPE);
                if (synapseType == null) {
                    log.warn("proofreader.removeRoiFromSynapse: No type value found on synapse: " + synapse.getAllProperties());
                }
                Double synapseConfidence = (Double) synapse.getProperty(CONFIDENCE);
                if (synapseConfidence == null) {
                    log.warn("proofreader.removeRoiFromSynapse: No confidence value found on synapse: " + synapse.getAllProperties());
                }

                // update connection set counts
                // get the connection sets that it's part of
                List<Node> connectionSetList = getConnectionSetsForSynapse(dbService, synapse);
                Map<String, Double> thresholdMap = getPreAndPostHPThresholdFromMetaNode(dataset);
                // change roiInfo for each connection set
                for (Node connectionSetNode : connectionSetList) {

                    String roiInfoString = (String) connectionSetNode.getProperty(ROI_INFO);
                    if (roiInfoString != null) {
                        String roiInfoJsonString = removeSynapseFromRoiInfoWithHP(roiInfoString, roiName, synapseType, synapseConfidence, thresholdMap.get(PRE_HP_THRESHOLD), thresholdMap.get(POST_HP_THRESHOLD));
                        connectionSetNode.setProperty(ROI_INFO, roiInfoJsonString);
                    } else {
                        log.warn("proofreader.removeRoiFromSynapse: No roi info found on connection set: " + connectionSetNode.getAllProperties());
                    }
                }

                // update roi info and roi properties on neuron/segment
                if (neuron != null) {

                    // update roi info
                    String roiInfoString = (String) neuron.getProperty(ROI_INFO);
                    if (roiInfoString != null) {

                        String roiInfoJsonString = removeSynapseFromRoiInfo(roiInfoString, roiName, synapseType);
                        neuron.setProperty(ROI_INFO, roiInfoJsonString);

                        // remove boolean property if no longer present on neuron
                        if (!roiInfoContainsRoi(roiInfoJsonString, roiName)) {
                            neuron.removeProperty(roiName);
                        }

                    } else {
                        log.warn("proofreader.removeRoiFromSynapse: No roi info found on neuron: " + neuron.getAllProperties());
                    }
                } else {
                    log.warn("proofreader.removeRoiFromSynapse: Synapse not connected to neuron: " + synapse.getAllProperties());
                }

                // update meta node
                String metaRoiInfoString = (String) metaNode.getProperty(ROI_INFO);
                if (metaRoiInfoString != null) {
                    String roiInfoJsonString = removeSynapseFromRoiInfo(metaRoiInfoString, roiName, synapseType);
                    metaNode.setProperty(ROI_INFO, roiInfoJsonString);
                } else {
                    log.warn("proofreader.removeRoiFromSynapse: No roi info found on meta node for dataset: " + dataset);
                }
            } else {
                log.warn("proofreader.removeRoiFromSynapse: roi not present on synapse. Ignoring update request: " + synapse.getAllProperties());
            }

        } catch (Exception e) {
            log.error("proofreader.removeRoiFromSynapse: " + e);
            throw new RuntimeException("proofreader.removeRoiFromSynapse: " + e);
        }

        log.info("proofreader.removeRoiFromSynapse: exit");

    }

    private String removeSynapseFromRoiInfo(String roiInfoString, String roiName, String synapseType) {
        Map<String, SynapseCounter> roiInfoMap = getRoiInfoAsMap(roiInfoString);
        RoiInfo roiInfo = new RoiInfo(roiInfoMap);

        if (synapseType.equals(PRE)) {
            roiInfo.decrementPreForRoi(roiName);
        } else if (synapseType.equals(POST)) {
            roiInfo.decrementPostForRoi(roiName);
        }

        return roiInfo.getAsJsonString();

    }

    private boolean roiInfoContainsRoi(String roiInfoString, String queriedRoi) {
        Map<String, SynapseCounter> roiInfoMap = getRoiInfoAsMap(roiInfoString);
        return roiInfoMap.containsKey(queriedRoi);
    }

    @Procedure(value = "temp.updateConnectionSetsAndWeightHP", mode = Mode.WRITE)
    @Description("temp.updateConnectionSetsAndWeightHP(connectionSetNode, datasetLabel) ")
    public void updateConnectionSetsAndWeightHP(@Name("connectionSetNode") Node connectionSetNode, @Name("datasetLabel") String datasetLabel) {

        log.info("temp.updateConnectionSetsAndWeightHP: entry");

        try {
            // get all synapses on connection set
            Set<Node> synapsesForConnectionSet = org.janelia.flyem.neuprintloadprocedures.GraphTraversalTools.getSynapsesForConnectionSet(connectionSetNode);

            Map<String, Double> thresholdMap = getPreAndPostHPThresholdFromMetaNode(datasetLabel);

            int postHP = setConnectionSetRoiInfoAndGetWeightHP(synapsesForConnectionSet, connectionSetNode, thresholdMap.get(PRE_HP_THRESHOLD), thresholdMap.get(POST_HP_THRESHOLD));

            // add postHP to ConnectsTo
            addPostHPToConnectsTo(connectionSetNode, postHP);
        } catch (Exception e) {
            log.error("temp.updateConnectionSetsAndWeightHP: " + e);
            throw new RuntimeException("temp.updateConnectionSetsAndWeightHP: " + e);
        }

        log.info("temp.updateConnectionSetsAndWeightHP: exit");
    }

    private void lookForSynapsesOnSynapseSet(Node newSynapseSet, Node sourceSynapseSet, Set<Synapse> currentSynapses, Set<Synapse> notFoundSynapses) {

        if (sourceSynapseSet.hasRelationship(RelationshipType.withName(CONTAINS), Direction.OUTGOING)) {
            for (Relationship containsRel : sourceSynapseSet.getRelationships(RelationshipType.withName(CONTAINS), Direction.OUTGOING)) {
                Node synapseNode = containsRel.getEndNode();
                List<Integer> synapseLocation = getNeo4jPointLocationAsLocationList((Point) synapseNode.getProperty(LOCATION));
                Synapse synapse = new Synapse(synapseLocation.get(0), synapseLocation.get(1), synapseLocation.get(2));

                if (currentSynapses.contains(synapse)) {
                    // remove this synapse from its old synapse set
                    containsRel.delete();
                    // add it to the new synapse set
                    newSynapseSet.createRelationshipTo(synapseNode, RelationshipType.withName(CONTAINS));
                    // remove this synapse from the not found set
                    notFoundSynapses.remove(synapse);
                }
            }
        } else {
            log.info("Empty synapse set found for source with id: " + sourceSynapseSet.getProperty(DATASET_BODY_ID));
        }
    }

    private List<Integer> getNeo4jPointLocationAsLocationList(Point neo4jPoint) {
        return neo4jPoint.getCoordinate().getCoordinate().stream().map(d -> (int) Math.round(d)).collect(Collectors.toList());
    }

    private Node acquireSegmentFromDatabase(Long nodeBodyId, String datasetLabel) throws NoSuchElementException, NullPointerException {
        Map<String, Object> parametersMap = new HashMap<>();
        parametersMap.put(BODY_ID, nodeBodyId);
        Map<String, Object> nodeQueryResult;
        Node foundNode;
        nodeQueryResult = dbService.execute("MATCH (node:`" + datasetLabel + "-Segment`{bodyId:$bodyId}) RETURN node", parametersMap).next();
        foundNode = (Node) nodeQueryResult.get("node");
        return foundNode;
    }

    private void removeAllLabels(Node node) {
        Iterable<Label> nodeLabels = node.getLabels();
        for (Label label : nodeLabels) {
            node.removeLabel(label);
        }
    }

    private void removeAllRelationshipsExceptTypesWithName(Node node, List<String> except) {
        Iterable<Relationship> nodeRelationships = node.getRelationships();
        for (Relationship relationship : nodeRelationships) {
            if (!except.contains(relationship.getType().name())) {
                relationship.delete();
            }
        }
    }

    private void removeAllRelationships(Node node) {
        Iterable<Relationship> nodeRelationships = node.getRelationships();
        for (Relationship relationship : nodeRelationships) {
            relationship.delete();
        }
    }
}

