const fs = require('fs');
const path = require('path');

const DB = __dirname;
const BASE_DATE = '2026-08-14';
const files = fs.readdirSync(DB).filter((name) => /^seed_.*\.sql$/.test(name));
const tables = new Map();
let sourceSql = '';

function stripLineComments(text) {
  let result = '';
  let quoted = false;
  for (let i = 0; i < text.length; i += 1) {
    if (text[i] === "'") {
      if (quoted && text[i + 1] === "'") {
        result += "''";
        i += 1;
        continue;
      }
      quoted = !quoted;
    }
    if (!quoted && text[i] === '-' && text[i + 1] === '-') {
      while (i < text.length && text[i] !== '\n') i += 1;
      result += '\n';
      continue;
    }
    result += text[i];
  }
  return result;
}

function splitTopLevel(text) {
  const values = [];
  let current = '';
  let depth = 0;
  let quoted = false;
  for (let i = 0; i < text.length; i += 1) {
    const char = text[i];
    if (char === "'") {
      if (quoted && text[i + 1] === "'") {
        current += "''";
        i += 1;
        continue;
      }
      quoted = !quoted;
      current += char;
      continue;
    }
    if (!quoted) {
      if (char === '(') depth += 1;
      if (char === ')') depth -= 1;
      if (char === ',' && depth === 0) {
        values.push(current.trim());
        current = '';
        continue;
      }
    }
    current += char;
  }
  values.push(current.trim());
  return values;
}

function decode(value) {
  if (value === 'NULL') return null;
  if (value === 'TRUE') return true;
  if (value === 'FALSE') return false;
  if (/^-?\d+$/.test(value)) return Number(value);
  const employee = value.match(/employee_no='(DT\d{2}-\d{4})'/);
  if (employee) return employee[1];
  if (value.startsWith("'") && value.endsWith("'")) {
    return value.slice(1, -1).replace(/''/g, "'");
  }
  return value;
}

function parseValueTuples(text, start) {
  const tuples = [];
  let depth = 0;
  let quoted = false;
  let tupleStart = -1;
  for (let i = start; i < text.length; i += 1) {
    const char = text[i];
    if (char === "'") {
      if (quoted && text[i + 1] === "'") {
        i += 1;
        continue;
      }
      quoted = !quoted;
      continue;
    }
    if (quoted) continue;
    if (char === '(') {
      if (depth === 0) tupleStart = i + 1;
      depth += 1;
    } else if (char === ')') {
      depth -= 1;
      if (depth === 0) tuples.push(text.slice(tupleStart, i));
    } else if (char === ';' && depth === 0) {
      return { tuples, end: i + 1 };
    }
  }
  throw new Error('Unterminated INSERT VALUES block');
}

for (const file of files) {
  const rawText = fs.readFileSync(path.join(DB, file), 'utf8');
  sourceSql += `\n${rawText}`;
  const text = stripLineComments(rawText);
  const pattern = /INSERT INTO\s+(\w+)\s*\(([^)]+)\)\s*VALUES/gi;
  let match;
  while ((match = pattern.exec(text)) !== null) {
    const table = match[1];
    const columns = match[2].split(',').map((column) => column.trim());
    const parsed = parseValueTuples(text, pattern.lastIndex);
    const rows = tables.get(table) || [];
    for (const tuple of parsed.tuples) {
      const values = splitTopLevel(tuple);
      if (values.length !== columns.length) {
        throw new Error(`${file}:${table} expected ${columns.length} values, got ${values.length}: ${tuple.slice(0, 200)}`);
      }
      rows.push(Object.fromEntries(columns.map((column, index) => [column, decode(values[index])])));
    }
    tables.set(table, rows);
    pattern.lastIndex = parsed.end;
  }
}

// seed_projects.sql의 부서별 현재 프로젝트 보정은 INSERT ... SELECT라서 일반 VALUES 파서에 잡히지 않는다.
// CASE 매핑을 SQL에서 직접 읽어 실제 적재 후와 같은 멤버 행을 구성한다.
const staffingBlock = sourceSql.match(/INSERT INTO project_members[\s\S]*?SELECT CASE u\.department([\s\S]*?)END,[\s\S]*?FROM users u[\s\S]*?AND NOT EXISTS \([\s\S]*?p\.status = 'ONGOING'[\s\S]*?\);/);
if (staffingBlock) {
  const departmentProjects = new Map();
  for (const match of staffingBlock[1].matchAll(/WHEN '([^']+)' THEN (\d+)/g)) {
    departmentProjects.set(match[1], Number(match[2]));
  }
  const projects = new Map((tables.get('projects') || []).map((project) => [project.id, project]));
  const members = tables.get('project_members') || [];
  const hasOngoing = new Set(members
    .filter((member) => member.status === 'ACTIVE' && member.left_at == null && projects.get(member.project_id)?.status === 'ONGOING')
    .map((member) => member.user_id));
  for (const user of tables.get('users') || []) {
    if (!['ACTIVE', 'ON_LEAVE'].includes(user.status) || hasOngoing.has(user.employee_no)) continue;
    const projectId = departmentProjects.get(user.department);
    if (projectId == null) throw new Error(`No current-project mapping for department ${user.department}`);
    members.push({
      project_id: projectId,
      user_id: user.employee_no,
      is_owner: false,
      role: null,
      status: 'ACTIVE',
      left_at: null,
      created_at: '2026-08-14 09:00:00',
      modified_at: '2026-08-14 09:00:00'
    });
  }
  tables.set('project_members', members);
}

const errors = [];
const warn = [];
const rows = (table) => tables.get(table) || [];
const date = (value) => value == null ? null : String(value).slice(0, 10);
const add = (code, detail) => errors.push(`${code}: ${detail}`);

function indexBy(table, key) {
  const index = new Map();
  for (const row of rows(table)) index.set(row[key], row);
  return index;
}

function unique(table, keys) {
  const seen = new Set();
  for (const row of rows(table)) {
    const key = keys.map((field) => row[field]).join('|');
    if (seen.has(key)) add('DUPLICATE', `${table}(${keys.join(',')})=${key}`);
    seen.add(key);
  }
}

const users = indexBy('users', 'employee_no');
const projects = indexBy('projects', 'id');
const schedules = indexBy('schedules', 'id');
const meetings = indexBy('meetings', 'id');
const posts = indexBy('project_posts', 'id');
const tasks = indexBy('tasks', 'id');
const notifications = indexBy('notifications', 'id');
const projectMembers = new Map();

for (const member of rows('project_members')) {
  projectMembers.set(`${member.project_id}|${member.user_id}`, member);
}

function knownUser(employee, context) {
  if (!users.has(employee)) add('UNKNOWN_USER', `${context}: ${employee}`);
}

function knownProject(projectId, context) {
  if (!projects.has(projectId)) add('UNKNOWN_PROJECT', `${context}: ${projectId}`);
}

function memberAt(projectId, employee, when) {
  const member = projectMembers.get(`${projectId}|${employee}`);
  if (!member) return false;
  if (date(member.created_at) > when) return false;
  return member.left_at == null || date(member.left_at) >= when;
}

function checkProjectActor(projectId, employee, when, context) {
  knownProject(projectId, context);
  knownUser(employee, context);
  if (projects.has(projectId) && users.has(employee) && !memberAt(projectId, employee, when)) {
    add('NOT_PROJECT_MEMBER', `${context}: project=${projectId}, user=${employee}, date=${when}`);
  }
}

unique('users', ['employee_no']);
unique('users', ['email']);
unique('project_members', ['project_id', 'user_id']);
unique('meetings', ['document_no']);
unique('meeting_attendees', ['meeting_id', 'user_id']);
unique('schedule_participants', ['schedule_id', 'user_id']);
unique('schedule_leaves', ['schedule_id']);
unique('leave_balances', ['user_id', 'year']);
unique('notifications', ['id']);

for (const project of rows('projects')) {
  if (project.start_date > project.target_date) add('DATE_ORDER', `project ${project.id}`);
  if (project.status === 'ONGOING' && project.target_date < BASE_DATE) add('STALE_ONGOING', `project ${project.id}`);
}

for (const member of rows('project_members')) {
  knownProject(member.project_id, `project_member`);
  knownUser(member.user_id, `project_member ${member.project_id}`);
  if (member.status === 'ACTIVE' && member.left_at != null) add('MEMBER_STATE', `${member.project_id}|${member.user_id}`);
  if (member.status === 'LEFT' && member.left_at == null) add('MEMBER_STATE', `${member.project_id}|${member.user_id}`);
  if (users.get(member.user_id)?.status === 'RESIGNED' && projects.get(member.project_id)?.status === 'ONGOING' && member.status === 'ACTIVE') {
    add('RESIGNED_ACTIVE_MEMBER', `${member.project_id}|${member.user_id}`);
  }
}

for (const milestone of rows('milestones')) {
  const project = projects.get(milestone.project_id);
  knownProject(milestone.project_id, 'milestone');
  if (project && (milestone.target_date < project.start_date || milestone.target_date > project.target_date)) {
    add('OUTSIDE_PROJECT_PERIOD', `milestone project=${milestone.project_id}, date=${milestone.target_date}`);
  }
  if (milestone.completed_at && date(milestone.completed_at) > BASE_DATE) add('FUTURE_COMPLETION', `milestone project=${milestone.project_id}`);
}

for (const task of rows('tasks')) {
  knownUser(task.assignee_id, 'task assignee');
  knownUser(task.creator_id, 'task creator');
  if (task.created_at && date(task.created_at) > task.due_date) add('DATE_ORDER', `task ${task.id}: created after due`);
  if (task.completed_at && task.created_at && task.completed_at < task.created_at) add('DATE_ORDER', `task ${task.id}: completed before created`);
  if (task.completed_at && date(task.completed_at) > BASE_DATE) add('FUTURE_COMPLETION', `task ${task.id}`);
  if (task.project_id == null) {
    if (task.assignee_id !== task.creator_id) add('PERSONAL_TASK_ASSIGNEE', `task ${task.id}`);
  } else {
    const when = date(task.created_at) || task.due_date;
    checkProjectActor(task.project_id, task.assignee_id, when, `task ${task.id} assignee`);
    checkProjectActor(task.project_id, task.creator_id, when, `task ${task.id} creator`);
    const project = projects.get(task.project_id);
    if (project && (task.due_date < project.start_date || task.due_date > project.target_date)) add('OUTSIDE_PROJECT_PERIOD', `task ${task.id}`);
  }
}

for (const post of rows('project_posts')) {
  checkProjectActor(post.project_id, post.author_id, date(post.created_at), `post ${post.id}`);
  const project = projects.get(post.project_id);
  if (project && (date(post.created_at) < project.start_date || date(post.created_at) > project.target_date)) add('OUTSIDE_PROJECT_PERIOD', `post ${post.id}`);
  if (post.modified_at && post.created_at && post.modified_at < post.created_at) add('DATE_ORDER', `post ${post.id}`);
}

for (const meeting of rows('meetings')) {
  checkProjectActor(meeting.project_id, meeting.author_id, meeting.meeting_date, `meeting ${meeting.id} author`);
  const project = projects.get(meeting.project_id);
  if (project && (meeting.meeting_date < project.start_date || meeting.meeting_date > project.target_date)) add('OUTSIDE_PROJECT_PERIOD', `meeting ${meeting.id}`);
}

for (const attendee of rows('meeting_attendees')) {
  const meeting = meetings.get(attendee.meeting_id);
  knownUser(attendee.user_id, `meeting attendee ${attendee.meeting_id}`);
  if (!meeting) add('UNKNOWN_MEETING', `attendee meeting=${attendee.meeting_id}`);
  if (meeting && !memberAt(meeting.project_id, attendee.user_id, meeting.meeting_date)) add('NOT_PROJECT_MEMBER', `meeting ${meeting.id} attendee=${attendee.user_id}`);
  const user = users.get(attendee.user_id);
  if (user && attendee.attendee_name !== user.name) add('STALE_SNAPSHOT', `meeting ${attendee.meeting_id} name=${attendee.user_id}`);
  if (user && attendee.attendee_department !== user.department) add('STALE_SNAPSHOT', `meeting ${attendee.meeting_id} department=${attendee.user_id}`);
  if (meeting && attendee.role === 'WRITER' && attendee.user_id !== meeting.author_id) add('WRITER_MISMATCH', `meeting ${meeting.id}`);
}

for (const expense of rows('expenses')) {
  checkProjectActor(expense.project_id, expense.spender_id, expense.expense_date, `expense ${expense.id}`);
  const project = projects.get(expense.project_id);
  if (project && (expense.expense_date < project.start_date || expense.expense_date > project.target_date)) add('OUTSIDE_PROJECT_PERIOD', `expense ${expense.id}`);
  if (expense.expense_date > BASE_DATE) add('FUTURE_EXPENSE', `expense ${expense.id}`);
  if (expense.amount <= 0) add('INVALID_AMOUNT', `expense ${expense.id}`);
}

const scheduleParticipants = new Map();
for (const participant of rows('schedule_participants')) {
  knownUser(participant.user_id, `schedule participant ${participant.schedule_id}`);
  if (!schedules.has(participant.schedule_id)) add('UNKNOWN_SCHEDULE', `participant schedule=${participant.schedule_id}`);
  const list = scheduleParticipants.get(participant.schedule_id) || [];
  list.push(participant);
  scheduleParticipants.set(participant.schedule_id, list);
}

for (const schedule of rows('schedules')) {
  knownUser(schedule.user_id, `schedule ${schedule.id}`);
  if (schedule.start_at > schedule.end_at) add('DATE_ORDER', `schedule ${schedule.id}`);
  if (schedule.created_at && (schedule.all_day ? date(schedule.created_at) > date(schedule.start_at) : schedule.created_at > schedule.start_at)) {
    add('CREATED_AFTER_EVENT', `schedule ${schedule.id}`);
  }
  const participants = scheduleParticipants.get(schedule.id) || [];
  const writers = participants.filter((participant) => participant.is_writer && participant.left_at == null);
  if (writers.length !== 1 || writers[0]?.user_id !== schedule.user_id) add('SCHEDULE_WRITER', `schedule ${schedule.id}`);
  if (date(schedule.start_at) >= BASE_DATE) {
    for (const participant of participants.filter((item) => item.left_at == null)) {
      if (users.get(participant.user_id)?.status === 'RESIGNED') add('RESIGNED_FUTURE_PARTICIPANT', `schedule ${schedule.id}|${participant.user_id}`);
    }
  }
}

for (const leave of rows('schedule_leaves')) {
  const schedule = schedules.get(leave.schedule_id);
  if (!schedule) add('UNKNOWN_SCHEDULE', `leave schedule=${leave.schedule_id}`);
  if (schedule && schedule.type !== 'PERSONAL') add('LEAVE_TYPE', `leave schedule=${leave.schedule_id}`);
  if (schedule) {
    const participants = scheduleParticipants.get(schedule.id) || [];
    if (!participants.some((participant) => participant.is_writer && participant.user_id === schedule.user_id)) {
      add('LEAVE_OWNER', `leave schedule=${leave.schedule_id}`);
    }
  }
}

for (const balance of rows('leave_balances')) knownUser(balance.user_id, 'leave balance');

for (const notification of rows('notifications')) {
  knownUser(notification.user_id, `notification ${notification.id} receiver`);
  if (notification.actor_id != null) knownUser(notification.actor_id, `notification ${notification.id} actor`);
  if (notification.read_at && notification.read_at < notification.created_at) add('READ_BEFORE_CREATED', `notification ${notification.id}`);
  if (notification.created_at && date(notification.created_at) > BASE_DATE) add('FUTURE_NOTIFICATION', `notification ${notification.id}`);
  if (notification.target_type === 'PROJECT' && notification.target_id != null && !projects.has(notification.target_id)) add('UNKNOWN_TARGET', `notification ${notification.id} project`);
  if (notification.target_type === 'MEETING' && notification.target_id != null && !meetings.has(notification.target_id)) add('UNKNOWN_TARGET', `notification ${notification.id} meeting`);
  if (notification.target_type === 'POST' && notification.target_id != null && !posts.has(notification.target_id)) add('UNKNOWN_TARGET', `notification ${notification.id} post`);
  if (notification.target_type === 'TASK' && notification.target_id != null && !tasks.has(notification.target_id)) add('UNKNOWN_TARGET', `notification ${notification.id} task`);
  if (notification.target_type === 'SCHEDULE' && notification.target_id != null && !schedules.has(notification.target_id)) add('UNKNOWN_TARGET', `notification ${notification.id} schedule`);
}

const ongoingProjectIds = new Set(rows('projects').filter((project) => project.status === 'ONGOING').map((project) => project.id));
const usersWithOngoingProject = new Set(rows('project_members').filter((member) => member.status === 'ACTIVE' && member.left_at == null && ongoingProjectIds.has(member.project_id)).map((member) => member.user_id));
for (const user of rows('users')) {
  if (['ACTIVE', 'ON_LEAVE'].includes(user.status) && !usersWithOngoingProject.has(user.employee_no)) {
    warn.push(`NO_ONGOING_PROJECT: ${user.employee_no}`);
  }
}

console.log(`files=${files.length}`);
for (const [table, tableRows] of [...tables.entries()].sort()) console.log(`${table}=${tableRows.length}`);
console.log(`errors=${errors.length}`);
console.log(`warnings=${warn.length}`);
for (const error of errors.slice(0, 500)) console.log(`ERROR ${error}`);
for (const warning of warn.slice(0, 500)) console.log(`WARN ${warning}`);
if (errors.length > 500) console.log(`... ${errors.length - 500} more errors`);
if (warn.length > 500) console.log(`... ${warn.length - 500} more warnings`);
process.exitCode = errors.length === 0 ? 0 : 1;
