package com.zjcds.common.db.au.datastore;


public interface UpdateableDatastore<T extends UpdateableDatastoreConnection> extends Datastore<T> {


    T getUpdateableDatastoreConnection() ;

}
