package org.janelia.flyem.neuprintprocedures;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.event.TransactionData;
import org.neo4j.graphdb.event.TransactionEventHandler;

import java.util.concurrent.ExecutorService;

public class MyTransactionEventHandler implements TransactionEventHandler {

    public static GraphDatabaseService dbService;
    private static ExecutorService executorService;

    public MyTransactionEventHandler(GraphDatabaseService graphDatabaseService, ExecutorService executorService) {
        dbService = graphDatabaseService;
        MyTransactionEventHandler.executorService = executorService;
    }

    @Override
    public Object beforeCommit(TransactionData transactionData) {
        return null;
    }

    @Override
    public void afterCommit(TransactionData transactionData, Object o) {
        TimeStampRunnable timeStampRunnable = new TimeStampRunnable(transactionData, dbService);
        executorService.submit(timeStampRunnable);
    }

    @Override
    public void afterRollback(TransactionData transactionData, Object o) {

    }

}