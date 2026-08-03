"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import {
  apiGetAdminMembers,
  apiGetAdminMember,
  apiSuspendMember,
  isAdmin,
  INDUSTRY_NAMES,
  type AdminMember,
} from "@/lib/api";
import AdminHeader from "@/components/AdminHeader";

const PAGE_SIZE = 10;

type SuspendFilter = "ALL" | "ACTIVE" | "SUSPENDED";
const FILTER_TO_PARAM: Record<SuspendFilter, boolean | undefined> = {
  ALL: undefined,
  ACTIVE: false,
  SUSPENDED: true,
};

export default function AdminMembersPage() {
  const router = useRouter();
  const [members, setMembers] = useState<AdminMember[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [suspending, setSuspending] = useState<string | null>(null);
  const [suspendError, setSuspendError] = useState("");
  const [searchInput, setSearchInput] = useState("");
  const [searchResult, setSearchResult] = useState<AdminMember[] | null>(null);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState("");
  const [suspendFilter, setSuspendFilter] = useState<SuspendFilter>("ALL");

  useEffect(() => {
    if (!isAdmin()) {
      router.replace("/login");
      return;
    }
    setLoading(true);
    setError("");
    apiGetAdminMembers(page, PAGE_SIZE, FILTER_TO_PARAM[suspendFilter])
      .then((data) => {
        const sorted = [...data.content].sort((a, b) => {
          if (a.role === "ADMIN" && b.role !== "ADMIN") return -1;
          if (a.role !== "ADMIN" && b.role === "ADMIN") return 1;
          return 0;
        });
        setMembers(sorted);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);
      })
      .catch(() => setError("데이터를 불러오지 못했어요"))
      .finally(() => setLoading(false));
  }, [page, suspendFilter, router]);

  const changeFilter = (f: SuspendFilter) => {
    setSuspendFilter(f);
    setPage(0);
  };

  const toggleSuspend = async (memberId: string, currentSuspended: boolean) => {
    setSuspending(memberId);
    setSuspendError("");
    setMembers((prev) =>
      prev.map((m) =>
        m.memberId === memberId ? { ...m, isSuspended: !currentSuspended } : m,
      ),
    );
    try {
      await apiSuspendMember(memberId);
    } catch (err: unknown) {
      setMembers((prev) =>
        prev.map((m) =>
          m.memberId === memberId ? { ...m, isSuspended: currentSuspended } : m,
        ),
      );
      setSuspendError((err as Error)?.message ?? "처리에 실패했어요");
    } finally {
      setSuspending(null);
    }
  };

  const handleSearch = async () => {
    const q = searchInput.trim();
    if (!q) {
      setSearchResult(null);
      setSearchError("");
      return;
    }
    setSearchLoading(true);
    setSearchError("");
    try {
      const member = await apiGetAdminMember(q);
      setSearchResult([member]);
    } catch {
      setSearchResult([]);
      setSearchError("일치하는 회원을 찾을 수 없어요");
    } finally {
      setSearchLoading(false);
    }
  };

  const clearSearch = () => {
    setSearchInput("");
    setSearchResult(null);
    setSearchError("");
  };

  const fmtDate = (iso: string) => iso.slice(0, 10);

  const renderRow = (m: AdminMember) => {
    const displayInd = INDUSTRY_NAMES[m.industry] ?? m.industry;
    return (
      <div
        key={m.memberId}
        style={{
          display: "grid",
          gridTemplateColumns: "1.4fr 1.5fr 1fr 1fr 0.9fr 0.7fr",
          alignItems: "center",
          padding: "13px 18px",
          borderTop: m.isSuspended ? "1px solid #f3c0bb" : "1px solid #f3f3f3",
          fontSize: 13,
          color: "#202124",
          background: m.isSuspended ? "#fff8f8" : "transparent",
        }}
      >
        <span
          style={{
            fontFamily: "monospace",
            fontSize: 11,
            color: m.isSuspended ? "#c5221f" : "#80868b",
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {m.memberId}
        </span>
        <span
          style={{
            color: m.isSuspended ? "#c5221f" : "#202124",
            fontWeight: m.isSuspended ? 600 : 400,
          }}
        >
          {m.email}
          {m.role === "ADMIN" && (
            <span
              style={{
                marginLeft: 6,
                display: "inline-block",
                padding: "1px 6px",
                borderRadius: 6,
                fontSize: 10,
                fontWeight: 700,
                background: "#e8f0fe",
                color: "#3b7ff2",
                verticalAlign: "middle",
              }}
            >
              ADMIN
            </span>
          )}
        </span>
        <span style={{ color: "#3c4043" }}>{displayInd}</span>
        <span style={{ color: "#5f6368" }}>{fmtDate(m.createdAt)}</span>
        <span>
          <span
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: 4,
              padding: "4px 10px",
              borderRadius: 10,
              fontSize: 11,
              fontWeight: 700,
              background: m.isSuspended ? "#c5221f" : "#e6f4ea",
              color: m.isSuspended ? "#fff" : "#137333",
              border: m.isSuspended ? "none" : "1px solid #b7dfcc",
            }}
          >
            {m.isSuspended && <span style={{ fontSize: 9 }}>●</span>}
            {m.isSuspended ? "정지됨" : "활성"}
          </span>
        </span>
        <span style={{ textAlign: "right" }}>
          {m.role !== "ADMIN" && (
            <button
              onClick={() => toggleSuspend(m.memberId, m.isSuspended)}
              disabled={suspending === m.memberId}
              style={{
                padding: "4px 12px",
                borderRadius: 8,
                fontSize: 12,
                fontWeight: 600,
                border: "none",
                cursor: suspending === m.memberId ? "default" : "pointer",
                background: m.isSuspended ? "#e6f4ea" : "#fce8e6",
                color: m.isSuspended ? "#137333" : "#c5221f",
                opacity: suspending === m.memberId ? 0.6 : 1,
              }}
            >
              {m.isSuspended ? "해제" : "정지"}
            </button>
          )}
        </span>
      </div>
    );
  };

  const sectionHeader = (
    label: string,
    count: number,
    color: string,
    bg: string,
  ) => (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 8,
        padding: "7px 18px",
        background: bg,
        borderTop: "1px solid #ebebeb",
      }}
    >
      <span
        style={{ fontSize: 11, fontWeight: 700, color, letterSpacing: 0.3 }}
      >
        {label}
      </span>
      <span style={{ fontSize: 11, color, opacity: 0.7 }}>({count}명)</span>
    </div>
  );

  const displayList = searchResult ?? members;
  const adminMembers = displayList.filter((m) => m.role === "ADMIN");
  const suspendedMembers = displayList.filter(
    (m) => m.isSuspended && m.role !== "ADMIN",
  );
  const activeMembers = displayList.filter(
    (m) => !m.isSuspended && m.role !== "ADMIN",
  );

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        background: "#fff",
        fontFamily: "Arial, 'Helvetica Neue', sans-serif",
      }}
    >
      <AdminHeader active="members" />

      <div style={{ flex: 1, padding: "24px 26px", overflowY: "auto" }}>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 12,
            marginBottom: 18,
          }}
        >
          <div
            style={{
              flex: 1,
              height: 40,
              maxWidth: 340,
              border: `1px solid ${searchResult !== null ? "#3b7ff2" : "#dadce0"}`,
              borderRadius: 20,
              display: "flex",
              alignItems: "center",
              gap: 10,
              padding: "0 16px",
            }}
          >
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              style={{ flexShrink: 0, cursor: "pointer" }}
              onClick={handleSearch}
            >
              <circle cx="11" cy="11" r="7" stroke="#9aa0a6" strokeWidth="2" />
              <line
                x1="16.5"
                y1="16.5"
                x2="21"
                y2="21"
                stroke="#9aa0a6"
                strokeWidth="2"
                strokeLinecap="round"
              />
            </svg>
            <input
              type="text"
              value={searchInput}
              onChange={(e) => {
                setSearchInput(e.target.value);
                if (!e.target.value) clearSearch();
              }}
              onKeyDown={(e) => e.key === "Enter" && handleSearch()}
              placeholder="이메일 또는 UUID로 검색"
              style={{
                flex: 1,
                border: "none",
                outline: "none",
                fontSize: 13,
                color: "#202124",
                background: "transparent",
              }}
            />
            {searchInput && (
              <button
                onClick={clearSearch}
                style={{
                  border: "none",
                  background: "none",
                  cursor: "pointer",
                  padding: 0,
                  fontSize: 15,
                  color: "#9aa0a6",
                  lineHeight: 1,
                }}
              >
                ✕
              </button>
            )}
          </div>
          <div style={{ display: "flex", gap: 6 }}>
            {(
              [
                { key: "ALL", label: "전체" },
                { key: "ACTIVE", label: "활성" },
                { key: "SUSPENDED", label: "정지" },
              ] as const
            ).map((f) => {
              const active = suspendFilter === f.key;
              return (
                <button
                  key={f.key}
                  onClick={() => changeFilter(f.key)}
                  style={{
                    padding: "6px 14px",
                    borderRadius: 16,
                    fontSize: 12.5,
                    fontWeight: active ? 600 : 400,
                    border: `1px solid ${active ? "#3b7ff2" : "#dadce0"}`,
                    background: active ? "#e8f0fe" : "#fff",
                    color: active ? "#3b7ff2" : "#5f6368",
                    cursor: "pointer",
                  }}
                >
                  {f.label}
                </button>
              );
            })}
          </div>
          <span
            style={{ marginLeft: "auto", fontSize: 12.5, color: "#9aa0a6" }}
          >
            총 {totalElements.toLocaleString("ko-KR")}명
          </span>
        </div>
        {suspendError && (
          <div
            style={{
              marginBottom: 12,
              padding: "8px 14px",
              background: "#fce8e6",
              border: "1px solid #f3c0bb",
              borderRadius: 8,
              fontSize: 13,
              color: "#c5221f",
            }}
          >
            {suspendError}
          </div>
        )}
        <div
          style={{
            border: "1px solid #ebebeb",
            borderRadius: 12,
            overflow: "hidden",
          }}
        >
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "1.4fr 1.5fr 1fr 1fr 0.9fr 0.7fr",
              padding: "12px 18px",
              background: "#f8f9fa",
              fontSize: 12,
              color: "#5f6368",
              fontWeight: 600,
            }}
          >
            <span>UUID</span>
            <span>이메일</span>
            <span>산업군</span>
            <span>가입일</span>
            <span>상태</span>
            <span style={{ textAlign: "right" }}>제재</span>
          </div>
          {loading || searchLoading ? (
            <div
              style={{
                padding: "30px 18px",
                textAlign: "center",
                fontSize: 13,
                color: "#9aa0a6",
              }}
            >
              로딩 중...
            </div>
          ) : error ? (
            <div
              style={{
                padding: "30px 18px",
                textAlign: "center",
                fontSize: 13,
                color: "#ea4c4c",
              }}
            >
              {error}
            </div>
          ) : searchError ? (
            <div
              style={{
                padding: "30px 18px",
                textAlign: "center",
                fontSize: 13,
                color: "#ea4c4c",
              }}
            >
              {searchError}
            </div>
          ) : displayList.length === 0 ? (
            <div
              style={{
                padding: "30px 18px",
                textAlign: "center",
                fontSize: 13,
                color: "#9aa0a6",
              }}
            >
              회원 없음
            </div>
          ) : (
            <>
              {adminMembers.length > 0 && (
                <>
                  {sectionHeader(
                    "관리자",
                    adminMembers.length,
                    "#3b7ff2",
                    "#f3f8ff",
                  )}
                  {adminMembers.map(renderRow)}
                </>
              )}
              {suspendedMembers.length > 0 && (
                <>
                  {sectionHeader(
                    "정지된 계정",
                    suspendedMembers.length,
                    "#c5221f",
                    "#fff8f8",
                  )}
                  {suspendedMembers.map(renderRow)}
                </>
              )}
              {activeMembers.length > 0 && (
                <>
                  {sectionHeader(
                    "활성 계정",
                    activeMembers.length,
                    "#137333",
                    "#f6fdf7",
                  )}
                  {activeMembers.map(renderRow)}
                </>
              )}
            </>
          )}
        </div>
        {searchResult === null && (
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              marginTop: 16,
            }}
          >
            <span style={{ fontSize: 12, color: "#9aa0a6" }}>
              {page * PAGE_SIZE + 1}–
              {Math.min((page + 1) * PAGE_SIZE, totalElements)} /{" "}
              {totalElements.toLocaleString("ko-KR")}
            </span>
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                style={{
                  width: 30,
                  height: 30,
                  border: "1px solid #dadce0",
                  borderRadius: 7,
                  background: "none",
                  cursor: page === 0 ? "default" : "pointer",
                  fontSize: 13,
                  color: page === 0 ? "#dadce0" : "#9aa0a6",
                }}
              >
                ‹
              </button>
              {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => {
                const p = Math.max(0, Math.min(page - 2, totalPages - 5)) + i;
                return (
                  <button
                    key={p}
                    onClick={() => setPage(p)}
                    style={{
                      width: 30,
                      height: 30,
                      border: p === page ? "none" : "1px solid #dadce0",
                      background: p === page ? "#3b7ff2" : "none",
                      color: p === page ? "#fff" : "#3c4043",
                      borderRadius: 7,
                      cursor: "pointer",
                      fontSize: 13,
                      fontWeight: p === page ? 600 : 400,
                    }}
                  >
                    {p + 1}
                  </button>
                );
              })}
              <button
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                style={{
                  width: 30,
                  height: 30,
                  border: "1px solid #dadce0",
                  borderRadius: 7,
                  background: "none",
                  cursor: page >= totalPages - 1 ? "default" : "pointer",
                  fontSize: 13,
                  color: page >= totalPages - 1 ? "#dadce0" : "#9aa0a6",
                }}
              >
                ›
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
