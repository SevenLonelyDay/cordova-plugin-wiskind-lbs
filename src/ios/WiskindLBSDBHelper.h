#import <Foundation/Foundation.h>
#import <FMDB/FMDB.h>

@interface WiskindLBSDBHelper : NSObject

/**
 * 开启
 *
 * @return 真，如果开启成功
 */
- (BOOL)open;

/**
 * 保存
 *
 * @param  lat   纬度
 * @param  lng   经度
 * @param  cdate 创建时间，时间戳
 * @return 真，如果保存成功
 */
- (BOOL)saveLat:(NSString *)lat lng:(NSString *)lng cdate:(NSInteger)cdate;

/**
 * 保存运行日志
 *
 * @return 真，如果保存成功
 */
- (BOOL)saveRunlog;

/**
 * 删除30天前的运行日志
 *
 * @return 真，如果删除成功
 */
- (BOOL)deleteRunlog;

/**
 * 关闭
 */
- (void)close;

@end
