import type { Metadata } from "next";
import { Baloo_2 } from "next/font/google";
import { NotificationListener } from "@/components/NotificationListener";
import "./globals.css";

const baloo2 = Baloo_2({
  subsets: ["latin"],
  variable: "--font-baloo2",
  weight: ["400", "500", "600", "700", "800"],
});

export const metadata: Metadata = {
  title: "Tangbisil",
  description: "검색으로 위장한 익명 직장인 매칭·채팅 서비스",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className={`${baloo2.variable} h-full`}>
      <body className="min-h-full flex flex-col">
        <NotificationListener />
        {children}
      </body>
    </html>
  );
}
