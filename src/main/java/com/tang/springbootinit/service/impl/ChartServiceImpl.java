package com.tang.springbootinit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tang.springbootinit.model.entity.Chart;
import com.tang.springbootinit.service.ChartService;
import com.tang.springbootinit.mapper.ChartMapper;
import org.springframework.stereotype.Service;

/**
 *
 */
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService{

}




