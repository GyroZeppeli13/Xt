package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author author
 * @since 2024-10-01
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;

    private final CourseClient courseClient;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        // 2.查询课表
        LearningLesson lesson = lessonService.queryByUserAndCourseId(userId, courseId);
        if(lesson == null) {
            throw new BizIllegalException("该课程未加入课表");
        }
        // 3.查询学习记录
        // select * from xx where lesson_id = #{lessonId}
        List<LearningRecord> records = lambdaQuery()
                .eq(LearningRecord::getLessonId, lesson.getId()).list();
        // 4.封装结果
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        dto.setRecords(BeanUtils.copyList(records, LearningRecordDTO.class));
        return dto;
    }

    @Override
    public void addLearningRecord(LearningRecordFormDTO formDTO) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        // 2.处理学习记录
        boolean finished = false;
        if (formDTO.getSectionType() == SectionType.VIDEO) {
            // 2.1.处理视频
            finished = handleVideoRecord(userId, formDTO);
        }else{
            // 2.2.处理考试
            finished = handleExamRecord(userId, formDTO);
        }

        // 3.处理课表数据
        handleLearningLessonsChanges(formDTO, finished);
    }

    private void handleLearningLessonsChanges(LearningRecordFormDTO formDTO, boolean finished) {
        // 1.查询课表
        LearningLesson lesson = lessonService.getById(formDTO.getLessonId());
        if (lesson == null) {
            throw new BizIllegalException("课程不存在，无法更新数据！");
        }
        // 2.判断是否有新的完成小节
        boolean allLearned = false;
        if(finished){
            // 3.如果有新完成的小节，则需要查询课程数据
            CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
            if (cInfo == null) {
                throw new BizIllegalException("课程不存在，无法更新数据！");
            }
            // 4.比较课程是否全部学完：已学习小节 >= 课程总小节
            allLearned = lesson.getLearnedSections() + 1 >= cInfo.getSectionNum();
        }
        // 5.更新课表
        lessonService.lambdaUpdate()
                .set(lesson.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .set(allLearned, LearningLesson::getStatus, LessonStatus.FINISHED.getValue())
                .set(LearningLesson::getLatestSectionId, formDTO.getSectionId())
                .set(LearningLesson::getLatestLearnTime, formDTO.getCommitTime())
                .setSql(finished, "learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO formDTO) {
        // 1.查询旧的学习记录
        LearningRecord old = queryOldRecord(formDTO.getLessonId(), formDTO.getSectionId());
        // 2.判断是否存在
        if (old == null) {
            // 3.不存在，则新增
            // 3.1.转换PO
            LearningRecord record = BeanUtils.copyBean(formDTO, LearningRecord.class);
            // 3.2.填充数据
            record.setUserId(userId);
            // 3.3.写入数据库
            boolean success = save(record);
            if (!success) {
                throw new DbException("新增学习记录失败！");
            }
            return false;
        }
        // 4.存在，则更新
        // 4.1.判断是否是第一次完成
        boolean finished = !old.getFinished() && formDTO.getMoment() * 2 >= formDTO.getDuration();
        // 4.2.更新数据
        boolean success = lambdaUpdate()
                .set(LearningRecord::getMoment, formDTO.getMoment())
                .set(finished, LearningRecord::getFinished, true)
                .set(finished, LearningRecord::getFinishTime, formDTO.getCommitTime())
                .eq(LearningRecord::getId, old.getId())
                .update();
        if(!success){
            throw new DbException("更新学习记录失败！");
        }
        return finished ;
    }

    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        return lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
    }

    private boolean handleExamRecord(Long userId, LearningRecordFormDTO formDTO) {
        // 1.转换DTO为PO
        LearningRecord record = BeanUtils.copyBean(formDTO, LearningRecord.class);
        // 2.填充数据
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(formDTO.getCommitTime());
        // 3.写入数据库
        boolean success = save(record);
        if (!success) {
            throw new DbException("新增考试记录失败！");
        }
        return true;
    }
}
