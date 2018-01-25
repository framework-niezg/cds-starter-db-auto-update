package com.zjcds.common.db.au.datastore;

import com.zjcds.common.db.au.datastore.enums.DsType;
import org.apache.metamodel.util.HasName;

import java.io.Serializable;

public interface Datastore<T extends DatastoreConnection> extends DatastoreMonitor,Serializable, HasName {

    @Override
    String getName();

    String getDescription();

    DsType getDatastoreType();

    T getDatastoreConnection();

    MetaDataNavigator getMetaDataNavigator();

}
