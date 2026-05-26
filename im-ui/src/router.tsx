import { createBrowserRouter } from "react-router-dom"
import MainLayout from "./layouts/MainLayout"
import SessionList from "./pages/chat/SessionList"
import ContactList from "./pages/contact/ContactList"
import Workspace from "./pages/workspace/Workspace"
import Me from "./pages/me/Me"
import Login from "./pages/auth/Login"
import Startup from "./pages/auth/Startup"
import Onboarding from "./pages/auth/Onboarding"
import ChatRoom from "./pages/chat/ChatRoom"
import ChatSettings from "./pages/chat/ChatSettings"
import NewFriends from "./pages/contact/NewFriends"
import UserProfile from "./pages/contact/UserProfile"
import GeneralSettings from "./pages/me/GeneralSettings"
import NotificationSettings from "./pages/me/NotificationSettings"
import GlobalSearch from "./pages/search/GlobalSearch"
import FileManager from "./pages/file/FileManager"
import GroupList from "./pages/contact/GroupList"
import Favorites from "./pages/me/Favorites"
import Blacklist from "./pages/contact/Blacklist"
import AdminDashboard from "./pages/admin/AdminDashboard"
import AdminAdvanced from "./pages/admin/AdminAdvanced"
import DepartmentDetail from "./pages/admin/DepartmentDetail"

export const router = createBrowserRouter([
  {
    path: "/",
    element: <MainLayout />,
    children: [
      {
        index: true,
        element: <SessionList />,
      },
      {
        path: "contact",
        element: <ContactList />,
      },
      {
        path: "workspace",
        element: <Workspace />,
      },
      {
        path: "me",
        element: <Me />,
      },
    ],
  },
  {
    path: "/auth/onboarding",
    element: <Onboarding />,
  },
  {
    path: "/auth/login",
    element: <Login />,
  },
  {
    path: "/auth/startup",
    element: <Startup />,
  },
  {
    path: "/chat/:id",
    element: <ChatRoom />,
  },
  {
    path: "/chat/settings/:id",
    element: <ChatSettings />,
  },
  {
    path: "/contact/new",
    element: <NewFriends />,
  },
  {
    path: "/contact/groups",
    element: <GroupList />,
  },
  {
    path: "/contact/blacklist",
    element: <Blacklist />,
  },
  {
    path: "/contact/profile/:id",
    element: <UserProfile />,
  },
  {
    path: "/settings",
    element: <GeneralSettings />,
  },
  {
    path: "/settings/notification",
    element: <NotificationSettings />,
  },
  {
    path: "/search",
    element: <GlobalSearch />,
  },
  {
    path: "/files",
    element: <FileManager />,
  },
  {
    path: "/me/favorites",
    element: <Favorites />,
  },
  {
    path: "/admin",
    element: <AdminDashboard />,
  },
  {
    path: "/admin/advanced",
    element: <AdminAdvanced />,
  },
  {
    path: "/admin/department/:id",
    element: <DepartmentDetail />,
  },
])
