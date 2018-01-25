package com.zjcds.common.db.au.datastore;

import org.apache.metamodel.UpdateableDataContext;

public interface UpdateableDatastoreConnection extends DatastoreConnection {

    UpdateableDataContext getUpdateableDataContext();

}
