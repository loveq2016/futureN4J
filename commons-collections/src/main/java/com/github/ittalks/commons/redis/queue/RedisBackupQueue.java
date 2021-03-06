package com.github.ittalks.commons.redis.queue;

import com.alibaba.fastjson.JSON;
import com.github.ittalks.commons.redis.RedisManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.util.List;
import java.util.logging.Logger;

/**
 * Created by 刘春龙 on 2017/3/5.
 *
 * 备份队列
 */
public class RedisBackupQueue extends BackupQueue {

    public static final Logger logger = Logger.getLogger(RedisBackupQueue.class.getName());

    private static final int REDIS_DB_IDX = 0;
    public static final String MARKER = "marker";

    private final String name;

    public RedisBackupQueue(String name) {
        this.name = name;
        initQueue();
    }

    /**
     * 添加备份队列循环标记
     */
    private void initQueue() {
        Jedis jedis = null;
        try {
            jedis = RedisManager.getResource(REDIS_DB_IDX);

            //创建备份队列循环标记
            Task.TaskState state = new Task.TaskState();
            Task task = new Task(this.name, RedisBackupQueue.MARKER, null, state);

            String taskJson = JSON.toJSONString(task);

            //注意`多点问题`，防止备份队列添加多个循环标记
            //这里使用redis的事务&乐观锁
            jedis.watch(this.name);//监视当前队列
            boolean isExists = jedis.exists(this.name);//查询当前队列是否存在

            logger.info("备份队列已存在！队列名：" + this.name);
            List<String> backQueueData = jedis.lrange(this.name, 0, -1);
            logger.info("====================================");
            logger.info("备份队列[" + this.name + "]数据:");
            backQueueData.forEach(entry -> {
                logger.info(entry);
            });
            logger.info("========================================");

            Transaction multi = jedis.multi();//开启事务
            if (!isExists) {//只有当前队列不存在，才执行lpush
                multi.lpush(this.name, taskJson);
            }
            List<Object> results = multi.exec();
            logger.info("线程[" + Thread.currentThread().getName() + "] - [备份队列循环标记添加]事务执行结果：" + ((results != null && results.size() > 0)? results.get(0) : "Fail"));
        } catch (Throwable e) {
            logger.info(e.getMessage());
            e.printStackTrace();
        } finally {
            if (jedis != null) {
                RedisManager.returnResource(jedis);
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Task popTask() {
        Jedis jedis = null;
        Task task = null;
        try {
            jedis = RedisManager.getResource(REDIS_DB_IDX);

            /**
             * 循环取出备份队列的一个元素：从队尾取出元素，并将其放置队首
             */
            String taskValue = jedis.rpoplpush(this.name, this.name);
            task = JSON.parseObject(taskValue, Task.class);
        } catch (Throwable e) {
            logger.info(e.getMessage());
            e.printStackTrace();
        } finally {
            if (jedis != null) {
                RedisManager.returnResource(jedis);
            }
        }
        return task;
    }

    @Override
    public void finishTask(Task task) {
        Jedis jedis = null;
        try {
            jedis = RedisManager.getResource(REDIS_DB_IDX);
            String taskJson = JSON.toJSONString(task);
            jedis.lrem(this.name, 0, taskJson);
        } catch (Throwable e) {
            logger.info(e.getMessage());
            e.printStackTrace();
        } finally {
            if (jedis != null) {
                RedisManager.returnResource(jedis);
            }
        }
    }


}
