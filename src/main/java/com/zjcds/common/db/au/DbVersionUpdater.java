package com.zjcds.common.db.au;

import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.zjcds.common.datastore.MetaDataNavigator;
import com.zjcds.common.datastore.enums.DsType;
import com.zjcds.common.datastore.factory.DataStoreFactory;
import com.zjcds.common.datastore.impl.JdbcDatastore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.metamodel.DataContext;
import org.apache.metamodel.UpdateCallback;
import org.apache.metamodel.UpdateScript;
import org.apache.metamodel.UpdateableDataContext;
import org.apache.metamodel.data.DataSet;
import org.apache.metamodel.data.Row;
import org.apache.metamodel.schema.ColumnType;
import org.apache.metamodel.schema.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.SQLException;
import java.util.List;

/**
 * created date：2017-08-05
 * @author niezhegang
 */
@Component
@Order(0)
@Setter
@Getter
public class DbVersionUpdater implements ApplicationRunner,ResourceLoaderAware,InitializingBean {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private JdbcDatastore jdbcDatastore ;

    private ResourceLoader resourceLoader;
    @Autowired
    private ConversionService conversionService;

    private static final String DefaultVersionTableName = "t_sys_db_version";

    private static final String DefaultVersionFieldName = "ver";
    //版本表名
    private String versionTableName = DefaultVersionTableName;
    //版本字段名
    private String versionFieldName = DefaultVersionFieldName;
    //初始化版本
    private Integer initVersion = 0;

    @Value("${com.zjcds.db.sql}")
    private String sqlDir;
    @Value("${com.zjcds.db.currentVersion}")
    private Integer currentDBVersion;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public DbVersionUpdater(DataSource dataSource) {
        jdbcDatastore = DataStoreFactory.createJdbcDatastore("系统配置库",dataSource);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.hasText(sqlDir,"数据库升级sql脚本所在目录位置未定义！");
        sqlDir = StringUtils.trim(sqlDir);
        if(! StringUtils.endsWith(sqlDir,"/")){
            sqlDir += sqlDir + "/";
        }
        Assert.notNull(currentDBVersion,"当前系统对应的数据库版本未定义！");
        Assert.isTrue(currentDBVersion > 0,"设置的当前数据库版本不能小于零！");
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            CheckResult checkResult = checkDBUpdate();
            if(checkResult.needUpdate()){
                logger.info("开始数据库表结构自动升级，从版本{}到版本{}",checkResult.getFromVersion(),checkResult.getToVersion());
                executeDBUpdate(checkResult);
            }
            logger.info("当前系统数据库表已升级到最新版本{}，数据库类型为{}",checkResult.getToVersion(),jdbcDatastore.getDatastoreType());
        }
        catch (Exception e){
            logger.error("数据库版本升级失败，数据库类型为"+jdbcDatastore.getDatastoreType(),e);
            throw new IllegalStateException("数据库版本升级失败",e);
        }
    }

    private void executeDBUpdate(CheckResult checkResult) {
        Integer executeVersion;
        File file;
        while (checkResult.needUpdate()){
            executeVersion = checkResult.getFromVersion() + 1;
            try {
                file = getExecuteSQLFile(executeVersion, jdbcDatastore.getDatastoreType());
                Integer count = Files.readLines(file, Charset.forName("UTF-8"), new LineProcessor<Integer>() {
                    private Integer count = 0;
                    private Integer lineNumber = 0;

                    @Override
                    public boolean processLine(String line) throws IOException {
                        try {
                            lineNumber++;
                            line = prepareProcessLine(line);
                            if (!skipProcess(line)) {
                                count += jdbcDatastore.update(line);
                            }
                        } catch (SQLException sqlException) {
                            throw new IOException("执行sql脚本第" + lineNumber + "行出错",sqlException);
                        }
                        return true;
                    }

                    @Override
                    public Integer getResult() {
                        return count;
                    }

                    private String prepareProcessLine(String line) {
                        String ret = StringUtils.trim(line);
                        ret = StringUtils.stripEnd(ret, ";");
                        return ret;
                    }

                    private boolean skipProcess(String line) {
                        boolean skip = false;
                        if (StringUtils.startsWithAny(line, "#", "-")) {
                            logger.debug("跳过注释行执行[{}]", line);
                            skip = true;
                        } else if (StringUtils.isBlank(line)) {
                            logger.debug("跳过空行执行");
                            skip = true;
                        }
                        return skip;
                    }
                });
                completeVersionUpdate(jdbcDatastore.getJdbcDatastoreConnection().getDataContext(),checkResult,executeVersion,count);
            }
            catch (IOException e){
                throw new IllegalStateException("数据库脚本升级到版本"+executeVersion+"失败，请通知管理员处理！",e);
            }
        }
    }

    private void completeVersionUpdate(UpdateableDataContext updateableDataContext,CheckResult checkResult,Integer executeVersion,Integer updateCount) {
        updateableDataContext.executeUpdate(new UpdateScript() {
            @Override
            public void run(UpdateCallback callback) {
                callback.update(updateableDataContext
                        .getDefaultSchema().getName(),versionTableName)
                        .value(versionFieldName,executeVersion)
                        .execute();
            }
        });
        //执行下一版本
        checkResult.setFromVersion(executeVersion);
        logger.info("数据库已经升级到版本{}，完成{}条数据更新！",executeVersion,updateCount);
    }

    private File getExecuteSQLFile(Integer executeVersion , DsType dsType) {
        Assert.notNull(dsType,"数据源类型不能为空！");
        Assert.notNull(executeVersion,"执行脚本版本号不能为空！");
        String filePath = getSqlDir() +dsType.name()+"_"+executeVersion+".sql";
        logger.info("升级脚本路径为{}",filePath);
        try {
            return resourceLoader.getResource(filePath).getFile();
        }
        catch (IOException e) {
            throw new IllegalArgumentException("脚本文件不能访问",e);
        }
    }

    /**
     * 检查系统数据库是否需要升级
     * @return
     */
    private CheckResult checkDBUpdate() {
        MetaDataNavigator metaDataNavigator = jdbcDatastore.getMetaDataNavigator();
        CheckResult checkResult = new CheckResult(currentDBVersion,currentDBVersion);
        //还未创建版本管理表
        if(metaDataNavigator.getTable(versionTableName) == null){
            createVersionTable(jdbcDatastore.getJdbcDatastoreConnection().getUpdateableDataContext());
        }
        Integer fromVersion = fromVersion(jdbcDatastore.getJdbcDatastoreConnection().getUpdateableDataContext());
        checkResult.setFromVersion(fromVersion);
        return checkResult;
    }

    private Integer fromVersion(DataContext dataContext){
        DataSet dataSet = dataContext.query().from(dataContext.getDefaultSchema(),versionTableName)
                            .select(versionFieldName).execute();
        List<Row> rows = dataSet.toRows();
        if(rows == null || rows.size() != 1){
            throw new IllegalStateException("版本表"+versionTableName+"中记录不正确！");
        }
        Row row = rows.get(0);
        Integer fromVersion = conversionService.convert(row.getValue(0),Integer.class);
        Assert.notNull(fromVersion ,"获取起始版本出错！");
        return fromVersion;
    }

    private void createVersionTable(UpdateableDataContext updateableDataContext){
        updateableDataContext.executeUpdate(new UpdateScript() {
            @Override
            public void run(UpdateCallback callback) {
                Table table = callback.createTable(callback.getDataContext().getDefaultSchema(),versionTableName)
                        .withColumn(versionFieldName).ofType(ColumnType.INTEGER).execute();
                callback.insertInto(table).value(versionFieldName,initVersion).execute();
            }
        });
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    private static class CheckResult {
        private Integer fromVersion;
        private Integer toVersion;
        public boolean needUpdate(){
            return toVersion > fromVersion;
        }
    }

}
