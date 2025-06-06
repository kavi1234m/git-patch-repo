/********************************************************************************************************
 * @file     GroupCell.h
 *
 * @brief    for TLSR chips
 *
 * @author   Telink, 梁家誌
 * @date     2018/7/31
 *
 * @par     Copyright (c) 2021, Telink Semiconductor (Shanghai) Co., Ltd. ("TELINK")
 *
 *          Licensed under the Apache License, Version 2.0 (the "License");
 *          you may not use this file except in compliance with the License.
 *          You may obtain a copy of the License at
 *
 *              http://www.apache.org/licenses/LICENSE-2.0
 *
 *          Unless required by applicable law or agreed to in writing, software
 *          distributed under the License is distributed on an "AS IS" BASIS,
 *          WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *          See the License for the specific language governing permissions and
 *          limitations under the License.
 *******************************************************************************************************/

#import <UIKit/UIKit.h>

@interface GroupCell : UITableViewCell
/// Text layer used to set groupID.
@property (weak, nonatomic) IBOutlet UILabel *groupID;
/// UISwitch used to show on off status of group.
@property (weak, nonatomic) IBOutlet UISwitch *sw;

/// Update content with model.
/// - Parameter model: model of cell.
- (void)updateContent:(SigGroupModel *)model;

@end
