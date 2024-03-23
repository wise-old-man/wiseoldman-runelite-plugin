package net.wiseoldman.beans;

import java.util.Set;
import lombok.Value;

import java.util.ArrayList;

@Value
public class GroupMemberAddition
{
	String verificationCode;
	ArrayList<Member> members;
	Set<RoleIndex> roleOrders;
}