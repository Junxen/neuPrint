package org.janelia.flyem.neuprinter.model;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A class for counting the the number of synaptic densities per roi. Used to produce
 * the synapseCountsPerRoi property on Neuron nodes in neo4j.
 */
public class SynapseCountsPerRoi {

    private Map<String, SynapseCounter> synapseCountsPerRoi;

    /**
     * Class constructor.
     */
    public SynapseCountsPerRoi() {
        this.synapseCountsPerRoi = new HashMap<>();
    }

    /**
     *
     * @return a map of rois to {@link SynapseCounter} instances
     */
    Map<String, SynapseCounter> getSynapseCountsPerRoi() {
        return this.synapseCountsPerRoi;
    }

    /**
     *
     * @return the set of all rois in this SynapseCountsPerRoi
     */
    private Set<String> getSetOfRois() {
        return this.synapseCountsPerRoi.keySet();
    }

    /**
     *
     * @param datasetLabel name of the dataset being evaluated
     * @return a set of all rois in this SynapseCountsPerRoi with and without "dataset-" prefix
     */
    public Set<String> getSetOfRoisWithAndWithoutDatasetLabel(String datasetLabel) {
        Set<String> roiPtSet = getSetOfRois();
        Set<String> roiPtSetWithDatasetPrefix = roiPtSet.stream()
                    .map(roi -> datasetLabel + "-" + roi)
                    .collect(Collectors.toSet());
        roiPtSetWithDatasetPrefix.addAll(roiPtSet);
        return roiPtSetWithDatasetPrefix;
    }

    /**
     * Adds provided roi to the SynapseCountsPerRoi mapped to a new {@link SynapseCounter} instance
     * initialized with pre and post equal to 0.
     *
     * @param roi roi name
     */
    private void addSynapseCountsForRoi(String roi) {
        this.synapseCountsPerRoi.put(roi, new SynapseCounter());
    }

    /**
     * Adds provided roi to the SynapseCountsPerRoi mapped to a new {@link SynapseCounter} instance
     * initialized with pre and post equal to provided values.
     *
     * @param roi roi name
     * @param pre presynaptic density count
     * @param post postsynaptic density count
     */
    public void addSynapseCountsForRoi(String roi, int pre, int post) {
        this.synapseCountsPerRoi.put(roi, new SynapseCounter(pre, post));
    }

    /**
     *
     * @param roi roi name
     * @return {@link SynapseCounter} for provided roi
     */
    SynapseCounter getSynapseCountsForRoi(String roi) {
        return this.synapseCountsPerRoi.get(roi);
    }

    /**
     * Increments the presynaptic density count for the provided roi by 1.
     *
     * @param roi roi name
     */
    void incrementPreForRoi(String roi) {
        if (!this.synapseCountsPerRoi.containsKey(roi)) {
            addSynapseCountsForRoi(roi);
        }
        this.synapseCountsPerRoi.get(roi).incrementPre();
    }

    /**
     * Increments the postsynaptic density count for the provided roi by 1.
     *
     * @param roi roi name
     */
    void incrementPostForRoi(String roi) {
        if (!this.synapseCountsPerRoi.containsKey(roi)) {
            addSynapseCountsForRoi(roi);
        }
        this.synapseCountsPerRoi.get(roi).incrementPost();
    }

    /**
     *
     * @return JSON of SynapseCountsPerRoi to be added as a synapseCountPerRoi property
     * on a node
     */
    public String getAsJsonString() {
        Gson gson = new Gson();
        return gson.toJson(this.synapseCountsPerRoi);
    }

    @Override
    public String toString() {
        return this.synapseCountsPerRoi.toString();
    }
}
