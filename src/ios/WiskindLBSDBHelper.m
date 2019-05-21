#import "WiskindLBSDBHelper.h"

@interface WiskindLBSDBHelper()

/** 数据库 */
@property (nonatomic, retain) FMDatabase *db;

@end

@implementation WiskindLBSDBHelper

- (BOOL)open {
    if (!self.db) {
        NSString *lib = [NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES) objectAtIndex: 0];
        NSFileManager *fileManager = [NSFileManager defaultManager];
        NSString *dir = [lib stringByAppendingPathComponent:@"LocalDatabase"];
        if (![fileManager fileExistsAtPath:dir]) {
            BOOL success = [fileManager createDirectoryAtPath:dir withIntermediateDirectories:NO attributes:nil error:nil];
            if (!success) {
                return NO;
            }
        }
        NSString *path = [dir stringByAppendingPathComponent:@"WiskindLBS.db"];
        self.db = [FMDatabase databaseWithPath:path];
        if (!self.db) {
            return NO;
        }
        if (![self.db open]) {
            return NO;
        };
    }
    if (![self existsTable:@"location"]) {
        NSString *sql = @"CREATE TABLE location (_id INTEGER PRIMARY KEY AUTOINCREMENT, lng TEXT, lat TEXT, cdate INTEGER)";
        BOOL success = [self.db executeUpdate:sql];
        if (!success) {
            return NO;
        }
    }
    if (![self existsTable:@"runlog"]) {
        NSString *sql = @"CREATE TABLE runlog (time INTEGER PRIMARY KEY, success INTEGER)";
        BOOL success = [self.db executeUpdate:sql];
        if (!success) {
            return NO;
        }
    }
    return YES;
}

- (BOOL)saveLat:(NSString *)lat lng:(NSString *)lng cdate:(NSInteger)cdate {
    NSString *sql = @"INSERT INTO location (lat, lng, cdate) VALUES (?, ?, ?)";
    return [self.db executeUpdate:sql, lat, lng, @(cdate)];
}

- (BOOL)saveRunlog {
    NSInteger ts = [[NSDate date] timeIntervalSince1970] * 1000;
    NSString *successSql = @"SELECT COUNT(*) > 0 FROM location WHERE cdate > ?";
    FMResultSet *rs = [self.db executeQuery:successSql, @(ts - 60000)];
    [rs next];
    BOOL success = [rs boolForColumnIndex:0];
    NSString *sql = @"INSERT INTO runlog (time, success) VALUES (?, ?)";
    return [self.db executeUpdate:sql, @(ts), @(success)];
}

- (BOOL)deleteRunlog {
    NSInteger ts = [[NSDate date] timeIntervalSince1970] * 1000;
    NSString *sql = @"DELETE FROM runlog WHERE time < ?";
    return [self.db executeUpdate:sql, @(ts - 2592000000)];
}

- (void)close {
    [self.db close];
}

/**
 * 是否存在表
 *
 * @param  tableName 表名
 * @return 真，如果存在表
 */
- (BOOL) existsTable:(NSString *)tableName
{
    FMResultSet *rs = [self.db executeQuery:@"SELECT count(*) as 'count' FROM sqlite_master WHERE type ='table' and name = ?", tableName];
    if ([rs next]) {
        return [rs intForColumn:@"count"];
    } else {
        return NO;
    }
}

@end
