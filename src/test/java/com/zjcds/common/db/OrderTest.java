package com.zjcds.common.db;

import com.zjcds.common.db.au.domain.ModulePropertiesConfig;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * created date：2018-03-28
 *
 * @author niezhegang
 */
public class OrderTest {
    @Test
    public void name() {
        List<ModulePropertiesConfig> modulePropertyConfigs = new ArrayList<>();
        ModulePropertiesConfig modulePropertiesConfig = ModulePropertiesConfig
                                                            .newBuilder()
                                                            .moduleName("test1")
                                                            .autoExec(true)
                                                            .currentVersion(1)
                                                            .order(0)
                                                            .build();
        modulePropertyConfigs.add(modulePropertiesConfig);
        modulePropertiesConfig = ModulePropertiesConfig
                .newBuilder()
                .moduleName("test2")
                .autoExec(true)
                .currentVersion(1)
                .order(Integer.MIN_VALUE + 10)
                .build();
        modulePropertyConfigs.add(modulePropertiesConfig);
        modulePropertiesConfig = ModulePropertiesConfig
                .newBuilder()
                .moduleName("test2")
                .autoExec(true)
                .currentVersion(1)
                .order(Integer.MIN_VALUE)
                .build();
        modulePropertyConfigs.add(modulePropertiesConfig);
        modulePropertyConfigs.sort(new Comparator<ModulePropertiesConfig>() {
            @Override
            public int compare(ModulePropertiesConfig o1, ModulePropertiesConfig o2) {
                Integer order1 = o1.getOrder();
                if(order1 == null)
                    order1 = Integer.MAX_VALUE;
                Integer order2 = o2.getOrder();
                if(order2 == null)
                    order2 = Integer.MAX_VALUE;
                return Integer.compare(order1,order2);
            }
        });
        System.out.println(modulePropertyConfigs);
    }
}
