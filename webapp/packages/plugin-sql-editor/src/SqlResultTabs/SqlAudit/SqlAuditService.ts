import { makeObservable, observable } from 'mobx';
import axios from 'axios';

import { ConnectionExecutionContextService, ConnectionInfoResource, createConnectionParam } from '@cloudbeaver/core-connections';
import { injectable } from '@cloudbeaver/core-di';
import { uuid } from '@cloudbeaver/core-utils';

import type { ISqlEditorTabState } from '../../ISqlEditorTabState';
import { SqlDataSourceService } from '../../SqlDataSource/SqlDataSourceService';

type AuditTask = {
  task_id: number;
  instance_db_type: string;
  pass_rate: number;
  score: number;
  audit_level: string;
};

type AuditTaskDesc = {
  number: number;
  audit_level: string;
  exec_sql: string;
  audit_result: AuditResult[];
}

type AuditResult = {
  level: string;
  message: string;
  rule_name: string;
}

type AuditTaskRes = {
  code: number;
  data: AuditTask;
  message: string;
};

type AuditTaskDescRes = {
  code: number;
  data: AuditTaskDesc[];
  message: string;
}

export type AuditTaskResult = {
  taskInfo: AuditTask | null
  taskDesc: AuditTaskDesc[] | null
}


@injectable()
export class SqlAuditService {
  auditData: Map<string, AuditTaskResult>;

  constructor(
    private readonly connectionExecutionContextService: ConnectionExecutionContextService,
    private readonly sqlDataSourceService: SqlDataSourceService,
    private readonly connectionInfoResource: ConnectionInfoResource,
  ) {
    this.auditData = new Map();

    makeObservable(this, {
      auditData: observable,
    });
  }

  async audit(editorState: ISqlEditorTabState, query: string): Promise<void> {
    const dataSource = this.sqlDataSourceService.get(editorState.editorId);
    const contextInfo = dataSource?.executionContext;

    const executionContext = contextInfo && this.connectionExecutionContextService.get(contextInfo.id);
    if (!contextInfo || !executionContext) {
      console.error('audit executionContext is not provided');
      return;
    }

    const connection = this.connectionInfoResource.get(createConnectionParam(contextInfo.projectId, contextInfo.connectionId));
    if (!connection) {
      console.error('audit connection is not provided');
      return
    }

    var task: AuditTaskResult = {
      taskInfo: null,
      taskDesc: null,
    };

    try {
      const split = connection.name.split(":", 2)
      if (split.length <= 1) {
        return
      }

      const { data, status } = await axios.post<AuditTaskRes>(
        "/sqle/v1/projects/" + split[0] + "/tasks/audits",
        { instance_name: split[1].trim(), instance_schema: contextInfo.defaultCatalog, sql: query},
        {
          headers: {
            'Accept': 'application/json',
            'Content-Type': 'multipart/form-data',
          },
        },
      );

      task.taskInfo = data.data
  
      console.log(JSON.stringify(data, null, 4));
  
      if (status != 200 || data.code != 0) {
        console.error('audit failed, status=' + status + ", message: " + data.message);
        return 
      }
  
    } catch (error) {

      if (axios.isAxiosError(error)) {
        console.log('error message: ', error.message);
        // return error.message;
      } else {
        console.log('unexpected error: ', error);
        // return 'An unexpected error occurred';
      }
    }

    try {
      const { data, status } = await axios.get<AuditTaskDescRes>(
        "/sqle/v2/tasks/audits/"+ task.taskInfo?.task_id + "/sqls?page_index=1&page_size=100",
        {
          headers: {
            'Accept': 'application/json',
          },
        }
      );

      console.log(JSON.stringify(data, null, 4));

      if (status != 200 || data.code != 0) {
        console.error('audit failed, status=' + status + ", message: " + data.message);
        return 
      }
      task.taskDesc = data.data

    } catch (error) {
      if (axios.isAxiosError(error)) {
        console.log('error message: ', error.message);
        // return error.message;
      } else {
        console.log('unexpected error: ', error);
        // return 'An unexpected error occurred';
      }
    }

    const tabId = this.createAuditTab(editorState, query);
    editorState.currentTabId = tabId;

    this.auditData.set(tabId, task);

    console.log(dataSource)
    
    console.log("audit: name[" + connection.name + "] sql["+ query +"], schema["+contextInfo.defaultCatalog+"]")
  }

  removeAuditTab(state: ISqlEditorTabState, tabId: string): void {
    const auditTab = state.auditTabs.find(auditTab => auditTab.tabId === tabId);

    if (auditTab) {
      state.auditTabs.splice(state.auditTabs.indexOf(auditTab), 1);
    }

    this.auditData.delete(tabId);
  }

  private createAuditTab(state: ISqlEditorTabState, query: string) {
    const dataSource = this.sqlDataSourceService.get(state.editorId);
    if (!dataSource) {
      throw new Error('SQL Data Source is not provided');
    }

    const id = uuid();
    const order = Math.max(0, ...state.tabs.map(tab => tab.order + 1));
    const nameOrder = Math.max(1, ...state.auditTabs.map(tab => tab.order + 1));

    state.auditTabs.push({
      tabId: id,
      order: nameOrder,
      query,
    });

    state.tabs.push({
      id,
      name: `Audit - ${nameOrder}`,
      icon: 'execution-plan-tab',
      order,
    });

    return id;
  }
}
