package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author 虎哥
 */
@RestController
@RequestMapping("/admin/questions")
@Api(tags = "互动问答管理端相关接口")
@RequiredArgsConstructor
public class InteractionQuestionAdminController {

    private final IInteractionQuestionService questionService;

    @ApiOperation("管理端分页查询互动问题")
    @GetMapping("page")
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query){
        return questionService.queryQuestionPageAdmin(query);
    }

    @ApiOperation("管理端隐藏或显示问题")
    @PutMapping("/{id}/hidden/{hidden}")
    public void hiddenOrShowQuestionAdmin(@PathVariable("id") Long id, @PathVariable("hidden") boolean hidden) {
        questionService.hiddenOrShowQuestionAdmin(id, hidden);
    }

    //queryQuestionById
    @ApiOperation("管理端根据id查询问题详情")
    @GetMapping("/{id}")
    public QuestionAdminVO queryQuestionByIdAdmin(@PathVariable("id") Long id) {
        return questionService.queryQuestionByIdAdmin(id);
    }


}