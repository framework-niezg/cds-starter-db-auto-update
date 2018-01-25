package com.zjcds.common.db.au.datastore;

import org.apache.metamodel.DataContext;

public interface DatastoreConnection {

    DataContext getDataContext();

    Datastore getDatastore();

    

}
