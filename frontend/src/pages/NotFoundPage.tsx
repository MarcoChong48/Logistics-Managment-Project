import { SearchX } from "lucide-react";
import { ButtonLink, EmptyState, PageHeader, Slip } from "../components/ui";

export function NotFoundPage() {
  return (
    <>
      <PageHeader title="Page not found" />
      <Slip>
        <EmptyState
          icon={<SearchX aria-hidden="true" className="size-7" />}
          title="This address does not lead to an Admin page."
          action={
            <ButtonLink level="main" to="/admin/dashboard">
              Open the dashboard
            </ButtonLink>
          }
        >
          Check the address, or choose a page in the menu.
        </EmptyState>
      </Slip>
    </>
  );
}
