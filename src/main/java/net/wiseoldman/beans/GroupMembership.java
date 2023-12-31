package net.wiseoldman.beans;

import lombok.Value;

@Value
public class GroupMembership
{
    int playerId;
    int groupId;
    String role;
    String createdAt;
    String updatedAt;
    GroupMemberInfo player;
}
