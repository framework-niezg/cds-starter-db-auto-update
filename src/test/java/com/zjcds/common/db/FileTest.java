package com.zjcds.common.db;

import org.apache.metamodel.util.Resource;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * created date：2018-03-27
 *
 * @author niezhegang
 */
public class FileTest {
    @Test
    public void name() {
        Path path = Paths.get("sql","dir","first","/second/fafa/");
        System.out.println(path.toString());
    }
}
