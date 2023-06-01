package cn.beehive.cell.base.service.impl;

import cn.beehive.base.domain.entity.CellDO;
import cn.beehive.base.domain.entity.CellPermissionDO;
import cn.beehive.base.enums.CellCodeEnum;
import cn.beehive.base.enums.CellPermissionTypeEnum;
import cn.beehive.base.enums.CellStatusEnum;
import cn.beehive.base.mapper.CellMapper;
import cn.beehive.base.util.FrontUserUtil;
import cn.beehive.cell.base.domain.vo.CellVO;
import cn.beehive.cell.base.hander.CellPermissionHandler;
import cn.beehive.cell.base.hander.converter.CellConverter;
import cn.beehive.cell.base.service.CellPermissionService;
import cn.beehive.cell.base.service.CellService;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author hncboy
 * @date 2023/5/29
 * Cell 业务接口实现类
 */
@Service
public class CellServiceImpl extends ServiceImpl<CellMapper, CellDO> implements CellService {

    @Resource
    private CellPermissionService cellPermissionService;

    @Override
    public List<CellVO> listCell() {
        List<CellDO> cellDOList = list(new LambdaQueryWrapper<CellDO>()
                // 不查询隐藏的
                .ne(CellDO::getStatus, CellStatusEnum.HIDDEN)
                // sort 降序
                .orderByDesc(CellDO::getSort));
        // 把 code 为空的过滤，数据库的数据没有匹配到枚举
        cellDOList = cellDOList.stream().filter(cellDO -> Objects.nonNull(cellDO.getCode())).toList();
        if (CollectionUtil.isEmpty(cellDOList)) {
            return Collections.emptyList();
        }

        // 全部的 cellCode
        List<String> cellCodes = cellDOList.stream().map(cellDO -> cellDO.getCode().getCode()).collect(Collectors.toList());

        // 查询这些 cell 的权限
        // TODO 缓存
        List<CellPermissionDO> cellPermissionDOList = cellPermissionService.list(new LambdaQueryWrapper<CellPermissionDO>()
                .select(CellPermissionDO::getCellCode, CellPermissionDO::getType)
                // 当前用户或任意用户有任意图纸权限或当前图纸权限
                .in(CellPermissionDO::getCellCode, CollectionUtil.addAll(cellCodes, CellPermissionHandler.ANY_CELL_CODE))
                .in(CellPermissionDO::getUserId, FrontUserUtil.getUserId(), CellPermissionHandler.ANY_USER_ID));

        // 获取 cellCode 权限 Map
        Map<String, List<CellPermissionTypeEnum>> cellCodePermissionMap = cellPermissionDOList.stream()
                .collect(Collectors.groupingBy(CellPermissionDO::getCellCode, Collectors.mapping(CellPermissionDO::getType, Collectors.toList())));

        return cellDOList.stream()
                // 过滤有权限的
                .filter(cellDO -> cellCodePermissionMap.containsKey(CellPermissionHandler.ANY_CELL_CODE) || cellCodePermissionMap.containsKey(cellDO.getCode().getCode()))
                .map(cellDO -> {
                    CellVO cellVO = CellConverter.INSTANCE.entityToVO(cellDO);
                    // 判断是否可以使用
                    cellVO.setIsCanUse(isCanUse(cellDO, cellCodePermissionMap));
                    return cellVO;
                }).toList();
    }

    /**
     * 判断是否可以使用
     *
     * @param cellDO                cell
     * @param cellCodePermissionMap cellCode 权限 Map
     * @return 是否可以使用
     */
    private boolean isCanUse(CellDO cellDO, Map<String, List<CellPermissionTypeEnum>> cellCodePermissionMap) {
        // 任意用户有这些图纸使用权限
        if (cellCodePermissionMap.containsKey(CellPermissionHandler.ANY_CELL_CODE)
                && cellCodePermissionMap.get(CellPermissionHandler.ANY_CELL_CODE).contains(CellPermissionTypeEnum.USE)) {
            return true;
        }
        // 当前用户有这些图纸使用权限
        return cellCodePermissionMap.containsKey(cellDO.getCode().getCode())
                && cellCodePermissionMap.get(cellDO.getCode().getCode()).contains(CellPermissionTypeEnum.USE);
    }
}