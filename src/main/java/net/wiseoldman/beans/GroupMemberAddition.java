package net.wiseoldman.beans;

import lombok.Value;

import java.util.ArrayList;

@Value
public class GroupMemberAddition
{
    String verificationCode;
    ArrayList<Member> members;
}