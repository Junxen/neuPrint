package org.janelia.flyem.connconvert;

import java.util.Comparator;

public class SortNeuronBySize implements Comparator<Neuron> {

    public int compare(Neuron a, Neuron b) {
        return b.getSize() - a.getSize();
    }

}