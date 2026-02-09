package net.wiseoldman.beans;

import lombok.Getter;

@Getter
public enum GroupErrorCode
{
	// 403: Forbidden
	OPTED_OUT_MEMBERS_FOUND,
	INCORRECT_VERIFICATION_CODE,
}