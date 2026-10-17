CREATE TABLE IF NOT EXISTS fpermissions_player_groups (
    uuid CHAR(36) CHARACTER SET ascii NOT NULL,
    group_name VARCHAR(64) NOT NULL,
    PRIMARY KEY (uuid, group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS fpermissions_player_permissions (
    uuid CHAR(36) CHARACTER SET ascii NOT NULL,
    permission VARCHAR(128) NOT NULL,
    PRIMARY KEY (uuid, permission)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
