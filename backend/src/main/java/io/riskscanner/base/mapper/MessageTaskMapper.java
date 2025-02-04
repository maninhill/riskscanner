package io.riskscanner.base.mapper;

import io.riskscanner.base.domain.MessageTask;
import io.riskscanner.base.domain.MessageTaskExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MessageTaskMapper {
    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table message_task
     *
     * @mbg.generated Thu Mar 11 14:08:10 CST 2021
     */
    long countByExample(MessageTaskExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table message_task
     *
     * @mbg.generated Thu Mar 11 14:08:10 CST 2021
     */
    int deleteByExample(MessageTaskExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table message_task
     *
     * @mbg.generated Thu Mar 11 14:08:10 CST 2021
     */
    int deleteByPrimaryKey(Integer id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table message_task
     *
     * @mbg.generated Thu Mar 11 14:08:10 CST 2021
     */
    int insert(MessageTask record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table message_task
     *
     * @mbg.generated Thu Mar 11 14:08:10 CST 2021
     */
    int insertSelective(MessageTask record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table message_task
     *
     * @mbg.generated Thu Mar 11 14:08:10 CST 2021
     */
    List<MessageTask> selectByExampleWithBLOBs(MessageTaskExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table message_task
     *
     * @mbg.generated Thu Mar 11 14:08:10 CST 2021
     */
    List<MessageTask> selectByExample(MessageTaskExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table message_task
     *
     * @mbg.generated Thu Mar 11 14:08:10 CST 2021
     */
    MessageTask selectByPrimaryKey(Integer id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table message_task
     *
     * @mbg.generated Thu Mar 11 14:08:10 CST 2021
     */
    int updateByExampleSelective(@Param("record") MessageTask record, @Param("example") MessageTaskExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table message_task
     *
     * @mbg.generated Thu Mar 11 14:08:10 CST 2021
     */
    int updateByExampleWithBLOBs(@Param("record") MessageTask record, @Param("example") MessageTaskExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table message_task
     *
     * @mbg.generated Thu Mar 11 14:08:10 CST 2021
     */
    int updateByExample(@Param("record") MessageTask record, @Param("example") MessageTaskExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table message_task
     *
     * @mbg.generated Thu Mar 11 14:08:10 CST 2021
     */
    int updateByPrimaryKeySelective(MessageTask record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table message_task
     *
     * @mbg.generated Thu Mar 11 14:08:10 CST 2021
     */
    int updateByPrimaryKeyWithBLOBs(MessageTask record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table message_task
     *
     * @mbg.generated Thu Mar 11 14:08:10 CST 2021
     */
    int updateByPrimaryKey(MessageTask record);
}