package com.taobao.tddl.executor;

import java.util.List;
import java.util.concurrent.Future;

import com.taobao.tddl.common.exception.TddlException;
import com.taobao.tddl.common.model.ExtraCmd;
import com.taobao.tddl.common.model.lifecycle.AbstractLifecycle;
import com.taobao.tddl.common.utils.GeneralUtil;
import com.taobao.tddl.common.utils.logger.Logger;
import com.taobao.tddl.common.utils.logger.LoggerFactory;
import com.taobao.tddl.executor.common.AtomicNumberCreator;
import com.taobao.tddl.executor.common.ExecutionContext;
import com.taobao.tddl.executor.common.ExecutorContext;
import com.taobao.tddl.executor.cursor.ISchematicCursor;
import com.taobao.tddl.executor.cursor.ResultCursor;
import com.taobao.tddl.executor.cursor.impl.QueryPlanResultCursor;
import com.taobao.tddl.executor.exception.DataAccessException;
import com.taobao.tddl.executor.utils.ExecUtils;
import com.taobao.tddl.monitor.Monitor;
import com.taobao.tddl.optimizer.OptimizerContext;
import com.taobao.tddl.optimizer.core.expression.ISelectable;
import com.taobao.tddl.optimizer.core.plan.IDataNodeExecutor;
import com.taobao.tddl.optimizer.core.plan.IQueryTree;
import com.taobao.tddl.optimizer.exceptions.EmptyResultFilterException;

public class MatrixExecutor extends AbstractLifecycle implements IExecutor {

    private final static Logger logger = LoggerFactory.getLogger(MatrixExecutor.class);

    /**
     * client端核心流程 解析 优化 执行
     * 
     * @param extraCmd
     * @param sql
     * @param context
     * @param createTxn
     * @param transactionSequence
     * @param closeResultSet
     * @return
     * @throws DataAccessException
     */
    public ResultCursor execute(String sql,

    ExecutionContext executionContext) throws TddlException {

        if (logger.isDebugEnabled()) {
            logger.warn("extraCmd:\n" + executionContext.getExtraCmds());
            logger.warn("ParameterContext:\n" + executionContext.getParams());
        }

        // client端核心流程y
        try {
            long time = System.currentTimeMillis();

            int explainIndex = explainIndex(sql);

            if (explainIndex > 0) {
                sql = sql.substring(explainIndex);
            }
            IDataNodeExecutor qc = parseAndOptimize(sql, executionContext);
            // System.out.println(qc);
            if (explainIndex > 0) {
                return createQueryPlanResultCursor(qc);
            }

            ISchematicCursor sc = ExecutorContext.getContext()
                .getTopologyExecutor()
                .execByExecPlanNode(qc, executionContext);

            ResultCursor rc = this.wrapResultCursor(qc, sc, executionContext);

            // 控制语句
            time = Monitor.monitorAndRenewTime(Monitor.KEY1, Monitor.TDDL_EXECUTE, Monitor.Key3Success, time);

            if (qc instanceof IQueryTree) {
                // 做下表名替换
                List columnsForResultSet = ((IQueryTree) qc).getColumns();
                if (((IQueryTree) qc).getAlias() != null) {
                    columnsForResultSet = ExecUtils.copySelectables(columnsForResultSet);
                    for (Object s : columnsForResultSet) {
                        ((ISelectable) s).setTableName(((IQueryTree) qc).getAlias());
                    }
                }
                rc.setOriginalSelectColumns(columnsForResultSet);
            }
            return rc;
        } catch (EmptyResultFilterException e) {
            // e.printStackTrace();
            return ResultCursor.EMPTY_RESULT_CURSOR;
        } catch (Exception e) {
            throw new TddlException(e);
        }
    }

    private ResultCursor createQueryPlanResultCursor(IDataNodeExecutor qc) {
        return new QueryPlanResultCursor(qc.toString(), null);
    }

    public static final String EXPLAIN = "explain";

    private int explainIndex(String sql) {
        String temp = sql;
        int i = 0;
        for (; i < temp.length(); ++i) {
            switch (temp.charAt(i)) {
                case ' ':
                case '\t':
                case '\r':
                case '\n':
                    continue;
            }
            break;
        }

        if (temp.toLowerCase().startsWith(EXPLAIN, i)) {
            return i + EXPLAIN.length();
        } else {
            return -1;
        }
    }

    public IDataNodeExecutor parseAndOptimize(String sql, ExecutionContext executionContext) throws TddlException {

        boolean cache = false;

        if ("True".equalsIgnoreCase(GeneralUtil.getExtraCmd(executionContext.getExtraCmds(),
            ExtraCmd.ConnectionExtraCmd.OPTIMIZER_CACHE))) cache = true;
        IDataNodeExecutor qc = OptimizerContext.getContext()
            .getOptimizer()
            .optimizeAndAssignment(sql, executionContext.getParams(), executionContext.getExtraCmds(), cache);

        return qc;
    }

    @Override
    public Future<ISchematicCursor> execByExecPlanNodeFuture(IDataNodeExecutor qc, ExecutionContext executionContext)
                                                                                                                     throws TddlException {
        return ExecutorContext.getContext().getTopologyExecutor().execByExecPlanNodeFuture(qc, executionContext);
    }

    @Override
    public ResultCursor execByExecPlanNode(IDataNodeExecutor qc, ExecutionContext executionContext)
                                                                                                   throws TddlException {
        if (logger.isDebugEnabled()) {
            logger.warn("extraCmd:\n" + executionContext.getExtraCmds());
            logger.warn("ParameterContext:\n" + executionContext.getParams());
        }

        // client端核心流程y
        try {
            long time = System.currentTimeMillis();

            ISchematicCursor sc = ExecutorContext.getContext()
                .getTopologyExecutor()
                .execByExecPlanNode(qc, executionContext);

            ResultCursor rc = this.wrapResultCursor(qc, sc, executionContext);

            // 控制语句
            time = Monitor.monitorAndRenewTime(Monitor.KEY1, Monitor.TDDL_EXECUTE, Monitor.Key3Success, time);

            if (qc instanceof IQueryTree) {
                // 做下表名替换
                List columnsForResultSet = ((IQueryTree) qc).getColumns();
                if (((IQueryTree) qc).getAlias() != null) {
                    columnsForResultSet = ExecUtils.copySelectables(columnsForResultSet);
                    for (Object s : columnsForResultSet) {
                        ((ISelectable) s).setTableName(((IQueryTree) qc).getAlias());
                    }
                }
                rc.setOriginalSelectColumns(columnsForResultSet);
            }
            return rc;
        } catch (EmptyResultFilterException e) {
            // e.printStackTrace();
            return ResultCursor.EMPTY_RESULT_CURSOR;
        } catch (Exception e) {
            throw new TddlException(e);
        }
    }

    @Override
    public ResultCursor commit(ExecutionContext executionContext) throws TddlException {
        return ExecutorContext.getContext().getTopologyExecutor().commit(executionContext);
    }

    @Override
    public ResultCursor rollback(ExecutionContext executionContext) throws TddlException {
        return ExecutorContext.getContext().getTopologyExecutor().rollback(executionContext);

    }

    @Override
    public Future<ResultCursor> commitFuture(ExecutionContext executionContext) throws TddlException {
        return ExecutorContext.getContext().getTopologyExecutor().commitFuture(executionContext);
    }

    @Override
    public Future<ResultCursor> rollbackFuture(ExecutionContext executionContext) throws TddlException {
        return ExecutorContext.getContext().getTopologyExecutor().rollbackFuture(executionContext);
    }

    protected ResultCursor wrapResultCursor(IDataNodeExecutor command, ISchematicCursor iSchematicCursor,
                                            ExecutionContext context) throws TddlException {
        ResultCursor cursor;
        // 包装为可以传输的ResultCursor
        if (command instanceof IQueryTree) {
            if (!(iSchematicCursor instanceof ResultCursor)) {

                cursor = new ResultCursor(iSchematicCursor, context);
            } else {
                cursor = (ResultCursor) iSchematicCursor;
            }

        } else {
            if (!(iSchematicCursor instanceof ResultCursor)) {
                cursor = new ResultCursor(iSchematicCursor, context);
            } else {
                cursor = (ResultCursor) iSchematicCursor;
            }
        }
        generateResultIdAndPutIntoResultSetMap(cursor);
        return cursor;
    }

    private void generateResultIdAndPutIntoResultSetMap(ResultCursor cursor) {
        int id = idGen.getIntegerNextNumber();
        cursor.setResultID(id);

    }

    /**
     * id 生成器
     */
    private AtomicNumberCreator idGen = AtomicNumberCreator.getNewInstance();

}
