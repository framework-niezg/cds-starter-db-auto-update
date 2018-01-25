package com.zjcds.common.db.au;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * created date：2018-01-25
 * @author niezhegang
 */
@Configuration
@Import(DbVersionUpdater.class)
public class DbAutoUpdateConfiguration {

}
